package com.drdisagree.teledrive.core.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.os.Build
import com.drdisagree.teledrive.BuildConfig
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.domain.model.Country
import com.drdisagree.teledrive.domain.model.LinkMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

class TdLibTelegramClient(
    private val context: Context,
    private val databaseKeyProvider: TdlibDatabaseKeyProvider,
    private val pacer: TelegramPacer
) : TelegramClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clientMutex = Mutex()

    @Volatile
    private var client: Client? = null

    @Volatile
    private var credentials: TelegramCredentials? = null

    @Volatile
    private var parametersReady = false

    @Volatile
    private var databaseDropped = false
    private var clientGeneration = 0

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Uninitialized)
    override val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow(TelegramConnectionState.CONNECTING)
    override val connectionState: StateFlow<TelegramConnectionState> =
        _connectionState.asStateFlow()

    private val updates = MutableSharedFlow<TdApi.Object>(
        extraBufferCapacity = 4096,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override suspend fun start(credentials: TelegramCredentials) {
        clientMutex.withLock {
            this.credentials = credentials
            val failed = _authState.value is TelegramAuthState.Failed
            if (client == null || failed || _authState.value == TelegramAuthState.Closed) {
                if (failed) {
                    client?.let { stale -> runCatching { stale.send(TdApi.Close()) { } } }
                    client = null
                }
                _authState.value = TelegramAuthState.Initializing
                parametersReady = false
                client = createClient()
            }
        }
    }

    private fun createClient(): Client {
        runCatching { Client.execute(TdApi.SetLogVerbosityLevel(1)) }
        val generation = ++clientGeneration
        return Client.create(
            { update -> handleUpdate(generation, update) },
            { throwable -> SafeLog.e(TAG, "Update handler error", throwable) },
            { throwable -> SafeLog.e(TAG, "TDLib exception", throwable) }
        )
    }

    private fun handleUpdate(generation: Int, update: TdApi.Object) {
        updates.tryEmit(update)
        when (update) {
            is TdApi.UpdateAuthorizationState ->
                handleAuthorizationState(generation, update.authorizationState)

            is TdApi.UpdateConnectionState -> if (generation == clientGeneration) {
                _connectionState.value = when (update.state) {
                    is TdApi.ConnectionStateWaitingForNetwork ->
                        TelegramConnectionState.WAITING_FOR_NETWORK

                    is TdApi.ConnectionStateConnecting,
                    is TdApi.ConnectionStateConnectingToProxy -> TelegramConnectionState.CONNECTING

                    is TdApi.ConnectionStateUpdating -> TelegramConnectionState.UPDATING
                    else -> TelegramConnectionState.READY
                }
            }
        }
    }

    private fun handleAuthorizationState(generation: Int, state: TdApi.AuthorizationState) {
        SafeLog.d(TAG, "Auth state ${state::class.simpleName} gen=$generation/$clientGeneration")
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters ->
                scope.launch { sendTdlibParameters() }

            is TdApi.AuthorizationStateWaitPhoneNumber ->
                _authState.value = TelegramAuthState.WaitingForPhoneNumber

            is TdApi.AuthorizationStateWaitCode -> {
                val info = state.codeInfo
                _authState.value = TelegramAuthState.WaitingForCode(
                    phoneNumber = info.phoneNumber,
                    channel = info.type.toChannel(),
                    codeLength = info.type.codeLength(),
                    resendTimeoutSeconds = info.timeout
                )
            }

            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                SafeLog.d(TAG, "QR login token refreshed")
                _authState.value = TelegramAuthState.WaitingForQrScan(state.link)
            }

            is TdApi.AuthorizationStateWaitEmailAddress ->
                _authState.value = TelegramAuthState.WaitingForEmailAddress

            is TdApi.AuthorizationStateWaitEmailCode -> {
                val info = state.codeInfo
                _authState.value = TelegramAuthState.WaitingForEmailCode(
                    emailPattern = info.emailAddressPattern,
                    codeLength = info.length.takeIf { it > 0 }
                )
            }

            is TdApi.AuthorizationStateWaitPassword ->
                _authState.value = TelegramAuthState.WaitingForPassword(
                    state.passwordHint?.takeIf { it.isNotEmpty() }
                )

            is TdApi.AuthorizationStateWaitRegistration ->
                _authState.value = TelegramAuthState.RegistrationRequired

            is TdApi.AuthorizationStateReady ->
                _authState.value = TelegramAuthState.Ready

            is TdApi.AuthorizationStateLoggingOut ->
                _authState.value = TelegramAuthState.LoggingOut

            is TdApi.AuthorizationStateClosing ->
                _authState.value = TelegramAuthState.Initializing

            is TdApi.AuthorizationStateClosed -> {
                if (generation == clientGeneration) {
                    client = null
                    parametersReady = false
                    _authState.value = TelegramAuthState.Closed
                }
            }

            else -> SafeLog.w(TAG, "Unhandled authorization state: ${state::class.simpleName}")
        }
    }

    private suspend fun sendTdlibParameters() {
        val creds = credentials ?: return
        var activeClient = client
        var waitedMs = 0
        while (activeClient == null && waitedMs < CLIENT_WAIT_LIMIT_MS) {
            delay(CLIENT_WAIT_STEP_MS.milliseconds)
            waitedMs += CLIENT_WAIT_STEP_MS.toInt()
            activeClient = client
        }
        if (activeClient == null) {
            _authState.value = TelegramAuthState.Failed("Telegram client did not start")
            return
        }
        val base = File(context.filesDir, "tdlib")
        val parameters = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = File(base, "db").absolutePath
            filesDirectory = File(base, "files").absolutePath
            databaseEncryptionKey = databaseKeyProvider.databaseKey()
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = creds.apiId
            apiHash = creds.apiHash
            systemLanguageCode = Locale.getDefault().toLanguageTag()
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            systemVersion = "Android ${Build.VERSION.RELEASE}"
            applicationVersion = BuildConfig.VERSION_NAME
        }
        activeClient.send(parameters) { result ->
            if (result is TdApi.Error) {
                SafeLog.e(TAG, "SetTdlibParameters failed: ${result.code}")
                if (result.code == WRONG_DATABASE_KEY_CODE && !databaseDropped) {
                    databaseDropped = true
                    SafeLog.w(TAG, "Dropping a session database this device cannot open")
                    base.deleteRecursively()
                    scope.launch { sendTdlibParameters() }
                } else {
                    _authState.value = TelegramAuthState.Failed(result.message)
                }
            } else {
                parametersReady = true
            }
        }
    }


    /**
     * TDLib answers a queued request whenever it reconnects, which on a dead
     * connection can be never. Every request therefore carries a deadline, so a
     * caller fails with an error it can show instead of waiting forever.
     */
    private suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): T {
        val activeClient = client ?: throw TelegramException(500, "Telegram client not started")
        return sendVia(activeClient, function)
    }

    private suspend fun <T : TdApi.Object> sendVia(
        target: Client,
        function: TdApi.Function<T>
    ): T {
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                target.send(function) { result ->
                    if (result is TdApi.Error) {
                        continuation.resumeWithException(
                            TelegramException.from(result.code, result.message)
                        )
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(result as T)
                    }
                }
            }
        } ?: throw TelegramException(
            REQUEST_TIMEOUT_CODE,
            "Telegram did not answer ${function::class.java.simpleName}"
        ).also { SafeLog.w(TAG, "Request timed out: ${function::class.java.simpleName}") }
    }

    override suspend fun countries(): List<Country> =
        send(TdApi.GetCountries()).countries
            .filterNot { it.isHidden }
            .flatMap { info ->
                info.callingCodes.map { code ->
                    Country(
                        isoCode = info.countryCode,
                        name = info.name,
                        callingCode = code
                    )
                }
            }
            .sortedBy { it.name }

    override suspend fun detectedCountryCode(): String? =
        send(TdApi.GetCountryCode()).text.takeIf { it.isNotBlank() }

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        send(
            TdApi.SetAuthenticationPhoneNumber(
                phoneNumber,
                TdApi.PhoneNumberAuthenticationSettings()
            )
        )
    }

    override suspend fun requestQrCodeAuthentication() {
        send(TdApi.RequestQrCodeAuthentication(LongArray(0)))
    }

    override suspend fun restartAuthentication() {
        val current = credentials ?: return
        runCatching { send(TdApi.Close()) }
        withTimeoutOrNull(CLOSE_WAIT_LIMIT_MS.milliseconds) {
            _authState.first { it == TelegramAuthState.Closed }
        }
        clientMutex.withLock {
            client = null
            parametersReady = false
        }
        File(context.filesDir, "tdlib").deleteRecursively()
        start(current)
    }

    override suspend fun submitEmailAddress(email: String) {
        send(TdApi.SetAuthenticationEmailAddress(email))
    }

    override suspend fun submitEmailCode(code: String) {
        send(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code)))
    }

    override suspend fun submitCode(code: String) {
        send(TdApi.CheckAuthenticationCode(code))
    }

    override suspend fun submitPassword(password: String) {
        send(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun resendCode() {
        send(TdApi.ResendAuthenticationCode(null))
    }

    /**
     * Signing out is a local decision. Telling Telegram is attempted first, but
     * a client that never started, or a server that does not answer, must not
     * leave the account stuck on this device with no way out.
     */
    override suspend fun logout() {
        runCatching { send(TdApi.LogOut()) }
            .onFailure { SafeLog.w(TAG, "Remote sign out failed, dropping session locally") }
        runCatching { send(TdApi.Close()) }
        withTimeoutOrNull(CLOSE_WAIT_LIMIT_MS.milliseconds) {
            _authState.first { it == TelegramAuthState.Closed }
        }
        clientMutex.withLock {
            client = null
            credentials = null
            _authState.value = TelegramAuthState.Closed
        }
        File(context.filesDir, "tdlib").deleteRecursively()
    }

    override suspend fun getCurrentUser(): TelegramUser {
        val user = send(TdApi.GetMe())
        return TelegramUser(
            id = user.id,
            firstName = user.firstName,
            lastName = user.lastName,
            username = user.usernames?.editableUsername?.takeIf { it.isNotEmpty() }
                ?: user.usernames?.activeUsernames?.firstOrNull(),
            phoneNumber = user.phoneNumber,
            isPremium = user.isPremium
        )
    }

    private val storageChatMutex = Mutex()

    /**
     * Replaces whatever TDLib had with the one route the app wants, so a proxy
     * removed here cannot keep being used behind the app's back.
     */
    override suspend fun applyProxy(proxy: TelegramProxy?) {
        if (client == null) return
        awaitAuthorizedOrParameters()
        runCatching {
            val existing = send<TdApi.Proxies>(TdApi.GetProxies())
            existing.proxies.forEach { send<TdApi.Ok>(TdApi.RemoveProxy(it.id)) }
        }
        if (proxy == null) {
            runCatching { send<TdApi.Ok>(TdApi.DisableProxy()) }
            return
        }
        send<TdApi.Proxy>(
            TdApi.AddProxy(proxy.host, proxy.port, true, proxy.toTdType())
        )
    }

    override suspend fun testProxy(proxy: TelegramProxy) {
        val request = TdApi.TestProxy(
            proxy.host,
            proxy.port,
            proxy.toTdType(),
            PROXY_TEST_DC,
            PROXY_TEST_TIMEOUT_SECONDS
        )
        val active = client?.takeIf { parametersReady } ?: throw TelegramException(
            PROXY_NEEDS_CLIENT_CODE,
            "Telegram is not initialized yet"
        )
        awaitAuthorizedOrParameters()
        sendVia(active, request)
    }

    /**
     * Changing the route leaves TDLib on its existing sockets, which were opened
     * the old way. Re-declaring the network type forces every connection to be
     * re-established, this time through the proxy that was just applied.
     */
    override suspend fun reconnect() {
        if (client == null || !parametersReady) return
        runCatching { send<TdApi.Ok>(TdApi.SetNetworkType(currentNetworkType())) }
            .onFailure { SafeLog.w(TAG, "Could not force a reconnect", it) }
    }

    private fun currentNetworkType(): TdApi.NetworkType {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return TdApi.NetworkTypeOther()
        val capabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            ?: return TdApi.NetworkTypeNone()
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TdApi.NetworkTypeWiFi()
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                TdApi.NetworkTypeMobile()

            else -> TdApi.NetworkTypeOther()
        }
    }

    private fun TelegramProxy.toTdType(): TdApi.ProxyType = when (type) {
        TelegramProxyType.SOCKS5 -> TdApi.ProxyTypeSocks5(username.orEmpty(), password.orEmpty())
        TelegramProxyType.MTPROTO -> TdApi.ProxyTypeMtproto(mtprotoSecret(secret.orEmpty()))
        TelegramProxyType.HTTP ->
            TdApi.ProxyTypeHttp(username.orEmpty(), password.orEmpty(), false)
    }

    /**
     * Shared links carry the secret base64url encoded, while TDLib documents it
     * as hexadecimal. Anything that is not already hex is decoded and re-encoded
     * so both forms of the same secret reach Telegram.
     */
    private fun mtprotoSecret(raw: String): String {
        val secret = raw.trim()
        val isHex = secret.isNotEmpty() &&
                secret.length % 2 == 0 &&
                secret.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        if (isHex) return secret.lowercase()
        return runCatching {
            Base64.decode(secret, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                .joinToString("") { "%02x".format(it) }
        }.getOrDefault(secret)
    }

    /**
     * Proxy calls are accepted once TDLib has its parameters, well before a
     * sign in, which is the point: without a route there is nothing to sign in
     * through.
     */
    private suspend fun awaitAuthorizedOrParameters() {
        if (_authState.value == TelegramAuthState.Ready) return
        withTimeoutOrNull(PROXY_READY_WAIT_MS.milliseconds) {
            authState.first { it != TelegramAuthState.Initializing }
        }
    }

    override suspend fun getLimits(): TelegramLimits =
        TelegramLimits.forPremium(getCurrentUser().isPremium)

    override suspend fun ensureStorageChat(knownChatId: Long?): Long =
        storageChatMutex.withLock {
            awaitAuthorized()
            knownChatId?.let { candidate ->
                if (isStorageChat(candidate)) {
                    runCatching {
                        val chat = send<TdApi.Chat>(TdApi.GetChat(candidate))
                        SafeLog.d(TAG, "Using drive '${chat.title}' #$candidate")
                    }
                    return@withLock candidate
                }
            }
            repeat(DISCOVERY_ATTEMPTS) { attempt ->
                findStorageChat()?.let { return@withLock it }
                if (attempt < DISCOVERY_ATTEMPTS - 1) delay(DISCOVERY_RETRY_MS.milliseconds)
            }
            val chat = send(
                TdApi.CreateNewSupergroupChat(
                    STORAGE_CHAT_TITLE,
                    false,
                    true,
                    STORAGE_CHAT_MARKER,
                    null,
                    0,
                    false
                )
            )
            SafeLog.d(TAG, "Created a new storage channel")
            chat.id
        }

    override suspend fun createStorageChannel(label: String): StorageChannel {
        awaitAuthorized()
        val title = driveTitle(label)
        val chat = send(
            TdApi.CreateNewSupergroupChat(
                title,
                false,
                true,
                STORAGE_CHAT_MARKER,
                null,
                0,
                false
            )
        )
        SafeLog.d(TAG, "Created an additional storage channel")
        return StorageChannel(chatId = chat.id, title = title, documentCount = 0)
    }

    override suspend fun editDocument(
        chatId: Long,
        messageId: Long,
        localPath: String,
        caption: String
    ): RemoteDocument {
        awaitAuthorized()
        val edited = paced {
            send<TdApi.Message>(
                TdApi.EditMessageMedia(
                    chatId,
                    messageId,
                    null,
                    TdApi.InputMessageDocument(
                        TdApi.InputFileLocal(localPath),
                        null,
                        true,
                        TdApi.FormattedText(caption, emptyArray())
                    )
                )
            )
        }
        return edited.toRemoteDocument()
            ?: throw TelegramException(500, "Edited message is not a document")
    }

    override suspend fun linkPreview(url: String, withImage: Boolean): LinkMetadata? = runCatching {
        awaitAuthorized()
        val preview = send<TdApi.LinkPreview>(
            TdApi.GetLinkPreview(TdApi.FormattedText(url, emptyArray()), null)
        )
        LinkMetadata(
            url = url,
            siteName = preview.siteName.takeIf { it.isNotEmpty() },
            title = preview.title.takeIf { it.isNotEmpty() },
            description = preview.description?.text?.takeIf { it.isNotEmpty() },
            imagePath = if (withImage) previewImagePath(preview) else null
        )
    }.getOrNull()

    private suspend fun previewImagePath(preview: TdApi.LinkPreview): String? {
        val photo = when (val type = preview.type) {
            is TdApi.LinkPreviewTypeArticle -> type.photo
            is TdApi.LinkPreviewTypePhoto -> type.photo
            else -> null
        } ?: return null
        val size = photo.sizes.lastOrNull() ?: return null
        val local = size.photo.local
        if (local?.isDownloadingCompleted == true) return local.path
        val file = send<TdApi.File>(
            TdApi.DownloadFile(size.photo.id, PREVIEW_PRIORITY, 0, 0, true)
        )
        return file.local?.path?.takeIf { it.isNotEmpty() }
    }

    override suspend fun renameStorageChannel(chatId: Long, label: String): String {
        awaitAuthorized()
        val title = driveTitle(label)
        send<TdApi.Ok>(TdApi.SetChatTitle(chatId, title))
        return title
    }

    override suspend fun fetchChannelPhoto(chatId: Long): String? {
        awaitAuthorized()
        return runCatching {
            val chat = send<TdApi.Chat>(TdApi.GetChat(chatId))
            val photo = chat.photo?.small ?: return null
            photo.local?.path?.takeIf { it.isNotEmpty() && File(it).exists() }?.let { return it }
            val downloaded = send<TdApi.File>(
                TdApi.DownloadFile(photo.id, PHOTO_PRIORITY, 0, 0, true)
            )
            downloaded.local?.path?.takeIf { it.isNotEmpty() && File(it).exists() }
        }.getOrNull()
    }

    override suspend fun chatExists(chatId: Long): Boolean? = try {
        awaitAuthorized()
        send<TdApi.Chat>(TdApi.GetChat(chatId))
        true
    } catch (e: TelegramException) {
        if (e.code == 400) false else null
    }

    override suspend fun deleteStorageChannel(chatId: Long) {
        awaitAuthorized()
        send<TdApi.Ok>(TdApi.DeleteChat(chatId))
        SafeLog.d(TAG, "Deleted a storage channel")
    }


    private fun driveTitle(label: String): String {
        val trimmed = label.trim()
            .removePrefix(STORAGE_CHAT_TITLE)
            .trim()
            .take(MAX_LABEL_LENGTH)
        return if (trimmed.isEmpty()) STORAGE_CHAT_TITLE else "$STORAGE_CHAT_TITLE $trimmed"
    }

    private fun isDriveTitle(title: String): Boolean =
        title == STORAGE_CHAT_TITLE || title.startsWith("$STORAGE_CHAT_TITLE ")

    /** Requests answer with an error until the client finishes signing in. */
    private suspend fun awaitAuthorized() {
        if (_authState.value == TelegramAuthState.Ready) return
        withTimeoutOrNull(AUTH_WAIT_MS.milliseconds) {
            authState.first { it == TelegramAuthState.Ready }
        }
    }

    /**
     * A channel counts as the drive when it carries the marker, or when it is
     * an own private channel with the drive title whose description never took.
     * The marker is written back so the next check is unambiguous.
     */
    override suspend fun listStorageChannels(knownChatIds: List<Long>): List<StorageChannel> {
        awaitAuthorized()

        val known = validateCandidates(knownChatIds.toSet())
        if (known.size == knownChatIds.size && known.isNotEmpty()) {
            SafeLog.d(TAG, "Confirmed ${known.size} known drives")
            return known.sortedByDescending { it.documentCount }
        }

        val candidates = LinkedHashSet<Long>()
        runCatching { send(TdApi.SearchChatsOnServer(STORAGE_CHAT_TITLE, CHAT_SEARCH_LIMIT)) }
            .getOrNull()?.chatIds?.forEach { candidates.add(it) }

        val fromSearch = validateCandidates(candidates)
        if (fromSearch.size >= knownChatIds.size && fromSearch.isNotEmpty()) {
            SafeLog.d(TAG, "Discovered ${fromSearch.size} drives by name")
            return fromSearch.sortedByDescending { it.documentCount }
        }

        for (chatList in listOf(TdApi.ChatListMain(), TdApi.ChatListArchive())) {
            loadEveryChat(chatList)
            runCatching { send(TdApi.GetChats(chatList, CHAT_LIST_LIMIT)) }
                .getOrNull()?.chatIds?.forEach { candidates.add(it) }
        }
        return validateCandidates(candidates)
            .sortedByDescending { it.documentCount }
            .also { found ->
                SafeLog.d(
                    TAG,
                    "Discovered ${found.size} drives from ${candidates.size} chats"
                )
            }
    }

    private suspend fun validateCandidates(candidates: Set<Long>): List<StorageChannel> =
        candidates.mapNotNull { chatId ->
            runCatching {
                val chat = send<TdApi.Chat>(TdApi.GetChat(chatId))
                val type = chat.type as? TdApi.ChatTypeSupergroup ?: return@runCatching null
                if (!type.isChannel || !isDriveTitle(chat.title)) return@runCatching null
                if (!isOwnPrivateChannel(type.supergroupId)) return@runCatching null
                StorageChannel(
                    chatId = chatId,
                    title = chat.title,
                    photoPath = chat.photo?.small?.local?.path?.takeIf { it.isNotEmpty() },
                    documentCount = documentCount(chatId)
                )
            }.getOrNull()
        }

    /**
     * User files only. The channel also holds the folder state and the key
     * backup, which are the app's own bookkeeping and never shown as files.
     */
    private suspend fun documentCount(chatId: Long): Int = runCatching {
        val total = searchCount(chatId, "")
        val internal = INTERNAL_DOCUMENT_NAMES.sumOf { name -> searchCount(chatId, name) }
        (total - internal).coerceAtLeast(0)
    }.getOrDefault(0)

    private suspend fun searchCount(chatId: Long, query: String): Int = send(
        TdApi.SearchChatMessages(
            chatId,
            null,
            query,
            null,
            0,
            0,
            1,
            TdApi.SearchMessagesFilterDocument()
        )
    ).totalCount

    private suspend fun isStorageChat(chatId: Long): Boolean = runCatching {
        val chat = send(TdApi.GetChat(chatId))
        SafeLog.d(TAG, "Validating stored storage chat")
        val type = chat.type as? TdApi.ChatTypeSupergroup ?: return@runCatching false
        if (!type.isChannel) return@runCatching false
        val fullInfo = send(TdApi.GetSupergroupFullInfo(type.supergroupId))
        if (fullInfo.description.contains(STORAGE_CHAT_MARKER)) return@runCatching true
        if (!isDriveTitle(chat.title)) return@runCatching false
        if (!isOwnPrivateChannel(type.supergroupId)) return@runCatching false
        writeMarker(chatId, fullInfo.description)
        true
    }.onFailure { SafeLog.d(TAG, "Stored storage chat rejected: ${it.message}") }
        .getOrDefault(false)

    private suspend fun isOwnPrivateChannel(supergroupId: Long): Boolean = runCatching {
        val supergroup = send(TdApi.GetSupergroup(supergroupId))
        supergroup.status is TdApi.ChatMemberStatusCreator &&
                supergroup.usernames?.activeUsernames.isNullOrEmpty()
    }.getOrDefault(false)

    private suspend fun writeMarker(chatId: Long, description: String) {
        if (description.contains(STORAGE_CHAT_MARKER)) return
        val updated = if (description.isBlank()) {
            STORAGE_CHAT_MARKER
        } else {
            description + MARKER_SEPARATOR + STORAGE_CHAT_MARKER
        }
        runCatching { send(TdApi.SetChatDescription(chatId, updated)) }
            .onSuccess { SafeLog.d(TAG, "Restored storage marker on existing channel") }
            .onFailure { SafeLog.w(TAG, "Could not restore storage marker", it) }
    }

    /**
     * Server side search finds the channel even before the local chat list has
     * synced, which is the case right after signing in. The cached list is only
     * a fallback for accounts where the title was changed.
     */
    private suspend fun findStorageChat(): Long? {
        val candidates = LinkedHashSet<Long>()

        runCatching { send(TdApi.SearchChatsOnServer(STORAGE_CHAT_TITLE, CHAT_SEARCH_LIMIT)) }
            .onFailure { SafeLog.d(TAG, "Server chat search failed: ${it.message}") }
            .getOrNull()?.chatIds?.forEach { candidates.add(it) }
        runCatching { send(TdApi.SearchChats(STORAGE_CHAT_TITLE, CHAT_SEARCH_LIMIT)) }
            .onFailure { SafeLog.d(TAG, "Local chat search failed: ${it.message}") }
            .getOrNull()?.chatIds?.forEach { candidates.add(it) }

        matchStorageChat(candidates)?.let { return it }

        for (chatList in listOf(TdApi.ChatListMain(), TdApi.ChatListArchive())) {
            loadEveryChat(chatList)
            runCatching { send(TdApi.GetChats(chatList, CHAT_LIST_LIMIT)) }
                .onFailure { SafeLog.d(TAG, "Chat list read failed: ${it.message}") }
                .getOrNull()?.chatIds?.forEach { candidates.add(it) }
        }
        SafeLog.d(TAG, "Storage chat scan checked ${candidates.size} chats")
        return matchStorageChat(candidates) ?: adoptStorageChat(candidates)
    }

    /**
     * Titles come from the local chat cache, but a description needs a full
     * info request per chat. Filtering by title first keeps that to the handful
     * of channels that can actually be the drive, instead of hundreds of
     * requests that would trip Telegram's flood limits and fail the real match.
     */
    private suspend fun matchStorageChat(chatIds: Collection<Long>): Long? {
        val titled = chatIds.mapNotNull { chatId ->
            runCatching {
                val chat = send(TdApi.GetChat(chatId))
                val type = chat.type as? TdApi.ChatTypeSupergroup ?: return@runCatching null
                if (!type.isChannel || chat.title != STORAGE_CHAT_TITLE) {
                    null
                } else {
                    chatId to type.supergroupId
                }
            }.getOrNull()
        }
        if (titled.isEmpty()) return null
        SafeLog.d(TAG, "Found ${titled.size} channels named like the drive")

        val marked = titled.filter { (_, supergroupId) -> hasStorageMarker(supergroupId) }
        val usable = marked.ifEmpty {
            titled.filter { (_, supergroupId) -> isOwnPrivateChannel(supergroupId) }
        }
        val chosen = usable.minByOrNull { it.second } ?: return null
        if (marked.isEmpty()) {
            val fullInfo = runCatching { send(TdApi.GetSupergroupFullInfo(chosen.second)) }
                .getOrNull()
            writeMarker(chosen.first, fullInfo?.description.orEmpty())
        }
        return chosen.first
    }

    private suspend fun hasStorageMarker(supergroupId: Long): Boolean = runCatching {
        send(TdApi.GetSupergroupFullInfo(supergroupId)).description
            .contains(STORAGE_CHAT_MARKER)
    }.onFailure { SafeLog.d(TAG, "Channel description unavailable: ${it.message}") }
        .getOrDefault(false)

    /**
     * TDLib loads chat list pages in the background and answers with error 404
     * once every chat is loaded, so each page needs a moment to land in the
     * local database before the next read.
     */
    private suspend fun loadEveryChat(chatList: TdApi.ChatList) {
        var page = 0
        while (page++ < MAX_CHAT_LIST_PAGES) {
            val loaded = runCatching { send(TdApi.LoadChats(chatList, CHAT_LIST_PAGE)) }
            if (loaded.isFailure) {
                SafeLog.d(TAG, "Chat list page $page ended: ${loaded.exceptionOrNull()?.message}")
                return
            }
            delay(CHAT_LIST_SETTLE_MS.milliseconds)
        }
    }

    /**
     * Last resort for a channel this app created whose marker is missing. Only
     * an own private channel with the drive title is adopted.
     */
    private suspend fun adoptStorageChat(chatIds: Collection<Long>): Long? {
        for (chatId in chatIds) {
            val adopted = runCatching {
                val chat = send(TdApi.GetChat(chatId))
                if (chat.title != STORAGE_CHAT_TITLE) return@runCatching null
                val type = chat.type as? TdApi.ChatTypeSupergroup ?: return@runCatching null
                if (!type.isChannel) return@runCatching null
                if (!isOwnPrivateChannel(type.supergroupId)) return@runCatching null
                val fullInfo = send(TdApi.GetSupergroupFullInfo(type.supergroupId))
                writeMarker(chatId, fullInfo.description)
                SafeLog.d(TAG, "Adopted existing storage channel")
                chatId
            }.getOrNull()
            if (adopted != null) return adopted
        }
        return null
    }

    override fun uploadDocument(
        chatId: Long,
        localPath: String,
        fileName: String,
        mimeType: String,
        caption: String,
        thumbnailPath: String?
    ): Flow<TelegramUploadEvent> = callbackFlow<TelegramUploadEvent> {
        val thumbnail = thumbnailPath?.let {
            TdApi.InputThumbnail(TdApi.InputFileLocal(it), THUMBNAIL_EDGE, THUMBNAIL_EDGE)
        }
        val content = TdApi.InputMessageDocument(
            TdApi.InputFileLocal(localPath),
            thumbnail,
            true,
            TdApi.FormattedText(caption, emptyArray())
        )
        var finished = false
        var sentMessageId: Long
        val subscribed = CompletableDeferred<Unit>()
        val pendingMessageId = CompletableDeferred<Long>()
        val pendingFile = CompletableDeferred<Int?>()

        val job = launch {
            updates.onSubscription { subscribed.complete(Unit) }.collect { update ->
                val pendingId = pendingMessageId.await()
                val pendingFileId = pendingFile.await()
                when (update) {
                    is TdApi.UpdateFile -> {
                        if (pendingFileId != null && update.file.id == pendingFileId) {
                            val total = update.file.size.takeIf { it > 0 }
                                ?: update.file.expectedSize
                            trySend(
                                TelegramUploadEvent.Progress(
                                    update.file.remote?.uploadedSize ?: 0,
                                    total
                                )
                            )
                        }
                    }

                    is TdApi.UpdateMessageSendSucceeded -> {
                        if (update.oldMessageId == pendingId) {
                            sentMessageId = update.message.id
                            val document = update.message.toRemoteDocument()
                            if (document != null) {
                                finished = true
                                trySend(TelegramUploadEvent.Completed(document))
                                close()
                            } else {
                                finished = true
                                close(
                                    TelegramException(500, "Sent message is not a document")
                                )
                            }
                        }
                    }

                    is TdApi.UpdateMessageSendFailed -> {
                        if (update.oldMessageId == pendingId) {
                            finished = true
                            close(
                                TelegramException.from(update.error.code, update.error.message)
                            )
                        }
                    }
                }
            }
        }

        subscribed.await()
        val pending: TdApi.Message =
            paced { send(TdApi.SendMessage(chatId, null, null, null, null, content)) }
        sentMessageId = pending.id
        pendingFile.complete((pending.content as? TdApi.MessageDocument)?.document?.document?.id)
        pendingMessageId.complete(pending.id)
        trySend(TelegramUploadEvent.Started(pending.id))

        awaitClose {
            job.cancel()
            if (!finished) {
                client?.send(
                    TdApi.DeleteMessages(chatId, longArrayOf(sentMessageId), true)
                ) { }
            }
        }
    }.buffer(capacity = 64)

    override suspend fun copyDocument(
        chatId: Long,
        remoteFileId: String,
        fileName: String,
        mimeType: String,
        caption: String
    ): RemoteDocument {
        val content = TdApi.InputMessageDocument(
            TdApi.InputFileRemote(remoteFileId),
            null,
            false,
            TdApi.FormattedText(caption, emptyArray())
        )
        val subscribed = CompletableDeferred<Unit>()
        val pendingMessageId = CompletableDeferred<Long>()

        val sent = coroutineScope {
            val outcome = async {
                withTimeoutOrNull(COPY_TIMEOUT_MS.milliseconds) {
                    updates.onSubscription { subscribed.complete(Unit) }.first { update ->
                        val pendingId = pendingMessageId.await()
                        (update is TdApi.UpdateMessageSendSucceeded &&
                                update.oldMessageId == pendingId) ||
                                (update is TdApi.UpdateMessageSendFailed &&
                                        update.oldMessageId == pendingId)
                    }
                }
            }
            subscribed.await()
            val pending: TdApi.Message =
                paced { send(TdApi.SendMessage(chatId, null, null, null, null, content)) }
            pendingMessageId.complete(pending.id)
            outcome.await() ?: pending
        }
        return when (sent) {
            is TdApi.UpdateMessageSendSucceeded ->
                sent.message.toRemoteDocument()
                    ?: throw TelegramException(500, "Copied message is not a document")

            is TdApi.UpdateMessageSendFailed ->
                throw TelegramException.from(sent.error.code, sent.error.message)

            is TdApi.Message -> sent.toRemoteDocument()
                ?: throw TelegramException(500, "Copy did not complete")

            else -> throw TelegramException(500, "Copy did not complete")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun downloadDocument(remoteFileId: String): Flow<TelegramDownloadEvent> =
        callbackFlow<TelegramDownloadEvent> {
            val subscribed = CompletableDeferred<Unit>()
            val downloadedFileId = CompletableDeferred<Int>()

            val job = launch {
                updates.onSubscription { subscribed.complete(Unit) }
                    .filterIsInstance<TdApi.UpdateFile>()
                    .filter { it.file.id == downloadedFileId.await() }
                    .collect { update ->
                        val local = update.file.local
                        val readyPath = update.file.readyPath()
                        if (readyPath != null) {
                            trySend(
                                TelegramDownloadEvent.Completed(readyPath, update.file.size)
                            )
                            close()
                        } else {
                            trySend(
                                TelegramDownloadEvent.Progress(
                                    local?.downloadedSize ?: 0,
                                    update.file.size.takeIf { it > 0 }
                                        ?: update.file.expectedSize
                                )
                            )
                        }
                    }
            }

            subscribed.await()
            val file: TdApi.File =
                send(TdApi.GetRemoteFile(remoteFileId, TdApi.FileTypeDocument()))
            val fileId = file.id
            downloadedFileId.complete(fileId)

            file.readyPath()?.let { path ->
                trySend(TelegramDownloadEvent.Completed(path, file.size))
                close()
            }

            if (!isClosedForSend) {
                if (file.local?.isDownloadingCompleted == true) {
                    send<TdApi.Ok>(TdApi.DeleteFile(fileId))
                }
                val started: TdApi.File =
                    send(TdApi.DownloadFile(fileId, DOWNLOAD_PRIORITY, 0, 0, false))
                started.readyPath()?.let { path ->
                    trySend(TelegramDownloadEvent.Completed(path, started.size))
                    close()
                }
            }

            awaitClose {
                job.cancel()
                client?.send(TdApi.CancelDownloadFile(fileId, true)) { }
            }
        }.buffer(capacity = 64)

    override suspend fun fetchThumbnail(chatId: Long, messageId: Long): ByteArray? {
        val message = runCatching { send(TdApi.GetMessage(chatId, messageId)) }.getOrNull()
            ?: return null
        val document = (message.content as? TdApi.MessageDocument)?.document ?: return null

        document.thumbnail?.file?.let { thumbnailFile ->
            val downloaded = runCatching {
                send(TdApi.DownloadFile(thumbnailFile.id, THUMBNAIL_PRIORITY, 0, 0, true))
            }.getOrNull()
            val path = downloaded?.local?.path?.takeIf { it.isNotEmpty() }
            if (path != null) {
                runCatching { File(path).readBytes() }.getOrNull()?.let { return it }
            }
        }
        return document.minithumbnail?.data
    }

    override suspend fun resolveFile(remoteFileId: String): TelegramFileInfo =
        send(TdApi.GetRemoteFile(remoteFileId, TdApi.FileTypeDocument())).toInfo()

    override suspend fun getFileInfo(fileId: Int): TelegramFileInfo =
        send(TdApi.GetFile(fileId)).toInfo()

    override suspend fun requestFileRange(fileId: Int, offset: Long, limit: Long) {
        send(TdApi.DownloadFile(fileId, STREAM_PRIORITY, offset, limit, false))
    }

    override suspend fun readFilePart(fileId: Int, offset: Long, count: Long): ByteArray =
        send(TdApi.ReadFilePart(fileId, offset, count)).data

    override suspend fun cancelFileDownload(fileId: Int) {
        send(TdApi.CancelDownloadFile(fileId, false))
    }

    override fun fileUpdates(fileId: Int): Flow<TelegramFileInfo> =
        updates.filterIsInstance<TdApi.UpdateFile>()
            .filter { it.file.id == fileId }
            .map { it.file.toInfo() }

    /**
     * Messages come from server side search rather than getChatHistory: the
     * history call answers from whatever TDLib happens to have cached, which
     * on a fresh session is the oldest handful of messages, and reports the
     * channel as nearly empty. Search always reflects the channel itself.
     */
    override suspend fun fetchDocuments(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): RemoteDocumentPage {
        if (fromMessageId == 0L) {
            runCatching { send<TdApi.Ok>(TdApi.OpenChat(chatId)) }
        }

        var found = searchMessages(chatId, fromMessageId, limit)
        var attempts = 0
        while (fromMessageId == 0L && found.messages.isEmpty() && attempts++ < HISTORY_RETRIES) {
            delay(HISTORY_RETRY_MS.milliseconds)
            found = searchMessages(chatId, fromMessageId, limit)
        }

        val documents = found.messages.mapNotNull { it.toRemoteDocument() }
        val oldest = found.messages.lastOrNull()?.id ?: 0L
        SafeLog.d(
            TAG,
            "Page from=$fromMessageId messages=${found.messages.size} " +
                    "mapped=${documents.size} next=$oldest"
        )
        return RemoteDocumentPage(
            documents = documents,
            nextFromMessageId = if (found.messages.isEmpty()) 0L else oldest
        )
    }

    private suspend fun searchMessages(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): TdApi.FoundChatMessages = send(
        TdApi.SearchChatMessages(
            chatId,
            null,
            "",
            null,
            fromMessageId,
            0,
            limit,
            null
        )
    )

    override suspend fun getDocument(chatId: Long, messageId: Long): RemoteDocument? =
        runCatching { send(TdApi.GetMessage(chatId, messageId)).toRemoteDocument() }
            .getOrNull()

    override suspend fun editCaption(chatId: Long, messageId: Long, caption: String) {
        paced {
            send(
                TdApi.EditMessageCaption(
                    chatId,
                    messageId,
                    null,
                    TdApi.FormattedText(caption, emptyArray()),
                    false
                )
            )
        }
    }

    override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        messageIds.chunked(DELETE_BATCH).forEach { batch ->
            paced { send(TdApi.DeleteMessages(chatId, batch.toLongArray(), true)) }
        }
    }

    /**
     * Message creation is what Telegram rate limits, so those calls queue behind
     * one pacer and a flood wait stops all of them, not just the caller that hit it.
     */
    private suspend fun <T> paced(block: suspend () -> T): T =
        pacer.paced { withRateLimitRetry(block) }

    private suspend fun <T> withRateLimitRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: TelegramException) {
                if (!e.isRateLimit || attempt++ >= RATE_LIMIT_RETRIES) throw e
                delay(
                    (((e.retryAfterSeconds
                        ?: RATE_LIMIT_FALLBACK_SECONDS) + 1) * 1000L).milliseconds
                )
            }
        }
    }

    private fun TdApi.File.toInfo(): TelegramFileInfo = TelegramFileInfo(
        fileId = id,
        sizeBytes = size.takeIf { it > 0 } ?: expectedSize,
        localPath = local?.path?.takeIf { it.isNotEmpty() },
        isDownloadingCompleted = local?.isDownloadingCompleted == true,
        downloadOffset = local?.downloadOffset ?: 0,
        downloadedPrefixSize = local?.downloadedPrefixSize ?: 0
    )

    private fun TdApi.Message.toRemoteDocument(): RemoteDocument? = when (val body = content) {
        is TdApi.MessageDocument -> remoteDocument(
            file = body.document.document,
            fileName = body.document.fileName,
            mimeType = body.document.mimeType,
            caption = body.caption?.text.orEmpty(),
            miniThumbnail = body.document.minithumbnail?.data
        )

        is TdApi.MessageAudio -> remoteDocument(
            file = body.audio.audio,
            fileName = body.audio.fileName,
            mimeType = body.audio.mimeType,
            caption = body.caption?.text.orEmpty(),
            miniThumbnail = body.audio.albumCoverMinithumbnail?.data
        )

        is TdApi.MessageVideo -> remoteDocument(
            file = body.video.video,
            fileName = body.video.fileName,
            mimeType = body.video.mimeType,
            caption = body.caption?.text.orEmpty(),
            miniThumbnail = body.video.minithumbnail?.data
        )

        is TdApi.MessagePhoto -> body.photo.sizes.maxByOrNull { it.photo.size }?.let { size ->
            remoteDocument(
                file = size.photo,
                fileName = "photo_$id.jpg",
                mimeType = "image/jpeg",
                caption = body.caption?.text.orEmpty(),
                miniThumbnail = body.photo.minithumbnail?.data
            )
        }

        else -> null
    }

    private fun TdApi.Message.remoteDocument(
        file: TdApi.File,
        fileName: String,
        mimeType: String,
        caption: String,
        miniThumbnail: ByteArray?
    ): RemoteDocument? {
        val remote = file.remote ?: return null
        if (remote.id.isNullOrEmpty()) return null
        return RemoteDocument(
            chatId = chatId,
            messageId = id,
            remoteFileId = remote.id,
            uniqueFileId = remote.uniqueId,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = file.size,
            caption = caption,
            dateSeconds = date,
            miniThumbnail = miniThumbnail
        )
    }

    private fun TdApi.AuthenticationCodeType.toChannel(): CodeDeliveryChannel = when (this) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> CodeDeliveryChannel.TELEGRAM_APP
        is TdApi.AuthenticationCodeTypeSms -> CodeDeliveryChannel.SMS
        is TdApi.AuthenticationCodeTypeSmsWord -> CodeDeliveryChannel.SMS_WORD
        is TdApi.AuthenticationCodeTypeSmsPhrase -> CodeDeliveryChannel.SMS_PHRASE
        is TdApi.AuthenticationCodeTypeCall -> CodeDeliveryChannel.CALL
        is TdApi.AuthenticationCodeTypeFlashCall -> CodeDeliveryChannel.FLASH_CALL
        is TdApi.AuthenticationCodeTypeMissedCall -> CodeDeliveryChannel.MISSED_CALL
        is TdApi.AuthenticationCodeTypeFragment -> CodeDeliveryChannel.FRAGMENT
        is TdApi.AuthenticationCodeTypeFirebaseAndroid -> CodeDeliveryChannel.FIREBASE
        else -> CodeDeliveryChannel.OTHER
    }

    private fun TdApi.AuthenticationCodeType.codeLength(): Int? = when (this) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> length
        is TdApi.AuthenticationCodeTypeSms -> length
        is TdApi.AuthenticationCodeTypeCall -> length
        is TdApi.AuthenticationCodeTypeMissedCall -> length
        is TdApi.AuthenticationCodeTypeFragment -> length
        is TdApi.AuthenticationCodeTypeFirebaseAndroid -> length
        else -> null
    }

    companion object {
        private const val TAG = "TdLibTelegramClient"
        private const val WRONG_DATABASE_KEY_CODE = 401
 const val CLIENT_WAIT_LIMIT_MS = 5_000
        private const val REQUEST_TIMEOUT_MS = 45_000L
        private const val PROXY_TEST_DC = 2
        private const val PROXY_NEEDS_CLIENT_CODE = 401
        private const val PROXY_TEST_TIMEOUT_SECONDS = 20.0
        private const val PROXY_READY_WAIT_MS = 10_000L
        private const val REQUEST_TIMEOUT_CODE = 408
        private const val COPY_TIMEOUT_MS = 30_000L
        private const val CLIENT_WAIT_STEP_MS = 10L
        private const val DOWNLOAD_PRIORITY = 16
        private const val THUMBNAIL_PRIORITY = 24
        private const val THUMBNAIL_EDGE = 320
        private const val DELETE_BATCH = 100
        private const val RATE_LIMIT_RETRIES = 5
        private const val RATE_LIMIT_FALLBACK_SECONDS = 5
        private const val STREAM_PRIORITY = 32
        private val MARKER_SEPARATOR = System.lineSeparator()
        private const val DISCOVERY_ATTEMPTS = 8
        private const val DISCOVERY_RETRY_MS = 1_500L
        private const val AUTH_WAIT_MS = 30_000L
        private const val CHAT_SEARCH_LIMIT = 50
        private const val CHAT_LIST_SETTLE_MS = 150L
        private const val CHAT_LIST_PAGE = 100
        private const val CHAT_LIST_LIMIT = 1_000
        private const val MAX_CHAT_LIST_PAGES = 50
        private val INTERNAL_DOCUMENT_NAMES = listOf(
            "teledrive.folders.json",
            "teledrive.keybackup"
        )
        private const val HISTORY_RETRIES = 3
        private const val HISTORY_RETRY_MS = 400L
        private const val PHOTO_PRIORITY = 16
        private const val MAX_LABEL_LENGTH = 48
        private const val STORAGE_CHAT_TITLE = "TeleDrive"
        private const val CLOSE_WAIT_LIMIT_MS = 5_000L
        private const val PREVIEW_PRIORITY = 1
        const val STORAGE_CHAT_MARKER = "#teledrive-storage"
    }
}

/**
 * A download only counts as ready when the bytes are still on disk. TDLib keeps
 * reporting a completed local copy after the file has been removed from its
 * cache directory, which otherwise surfaces as a download that finishes
 * instantly and then cannot be found.
 */
private fun TdApi.File.readyPath(): String? {
    val local = this.local ?: return null
    if (!local.isDownloadingCompleted) return null
    val path = local.path
    if (path.isNullOrEmpty()) return null
    return path.takeIf { File(it).exists() }
}
