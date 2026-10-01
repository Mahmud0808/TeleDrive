package com.drdisagree.teledrive.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.publish.PublishScheduler
import com.drdisagree.teledrive.core.telegram.MessageChange
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.RemoteDocumentPage
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

internal const val TEST_CHAT = 77L

internal class SyncHarness : AutoCloseable {
    private val dbFile = File.createTempFile("teledrive-sync", ".db")
    val database: TeleDriveDatabase = Room.databaseBuilder<TeleDriveDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    val settings = FakeSettings(UserPreferences(storageChatId = TEST_CHAT))
    val telegram = FakeTelegram()
    val scheduler = CountingScheduler()
    val codec = ManifestCodec(StreamCrypto(), unused<WrappedKeyRepository>())
    val activeChannel = ActiveChannel(settings)
    val folderPaths = FolderPathResolver(
        database.folderDao(),
        activeChannel,
        database.folderTombstoneDao()
    )
    val storagePaths = FakeStoragePaths()
    val folderState = FolderStateSynchronizer(
        storagePaths,
        telegram,
        database.folderDao(),
        database.folderTombstoneDao(),
        settings,
        StreamCrypto(),
        unused<WrappedKeyRepository>()
    )

    fun newSync() = SyncRepositoryImpl(
        telegramClient = telegram,
        fileDao = database.fileDao(),
        manifestCodec = codec,
        pendingDeleteDao = database.pendingDeleteDao(),
        filePartDao = database.filePartDao(),
        folderDao = database.folderDao(),
        folderPathResolver = folderPaths,
        activeChannel = activeChannel,
        channelOwnership = ChannelOwnership(
            database.fileDao(),
            database.folderDao(),
            database.exclusionDao()
        ),
        folderStateSynchronizer = folderState,
        publishScheduler = scheduler,
        settingsRepository = settings,
        database = database
    )

    fun caption(manifest: RemoteFileManifest) = codec.encode(manifest, encrypt = false)

    override fun close() {
        database.close()
        dbFile.delete()
        telegram.cleanUp()
        storagePaths.cacheDir.deleteRecursively()
    }
}

internal fun manifest(
    id: String,
    name: String,
    size: Long = 4_000_000,
    folderPath: String = "",
    folderId: String? = null,
    modifiedAt: Long = 2_000
) = RemoteFileManifest(
    fileId = id,
    name = name,
    folderPath = folderPath,
    folderId = folderId,
    mimeType = "application/octet-stream",
    sizeBytes = size,
    createdAt = 1_000,
    modifiedAt = modifiedAt
)

internal fun document(messageId: Long, caption: String, size: Long = 4_000_000) = RemoteDocument(
    chatId = TEST_CHAT,
    messageId = messageId,
    remoteFileId = "remote-$messageId",
    uniqueFileId = "unique-$messageId",
    fileName = "file-$messageId",
    mimeType = "application/octet-stream",
    sizeBytes = size,
    caption = caption,
    dateSeconds = 100
)

internal class FakeTelegram : TelegramClient by unused() {
    var documents: List<RemoteDocument> = emptyList()
    val editedCaptions = mutableMapOf<Long, String>()
    val uploads = mutableListOf<Pair<String, String>>()
    val deletedMessages = mutableListOf<Long>()
    var failDownloads = false
    val changes = MutableSharedFlow<MessageChange>(extraBufferCapacity = 1_000)
    var openedChats = 0
    var closedChats = 0
    private var nextMessageId = 2_000_000L
    var onFetch: suspend (fromMessageId: Long) -> Unit = {}
    private val stateFiles = mutableListOf<File>()
    private var folderStateDocument: RemoteDocument? = null
    private var folderStateFile: File? = null

    fun publishFolderState(state: RemoteFolderState, messageId: Long = 1_000_000) {
        val file = File.createTempFile("folders", ".json").also { stateFiles += it }
        file.writeText(Json.encodeToString(RemoteFolderState.serializer(), state))
        folderStateFile = file
        folderStateDocument = RemoteDocument(
            chatId = TEST_CHAT,
            messageId = messageId,
            remoteFileId = "folder-state",
            uniqueFileId = "folder-state-$messageId",
            fileName = RemoteFolderState.FILE_NAME,
            mimeType = "application/json",
            sizeBytes = file.length(),
            caption = RemoteFolderState.MARKER,
            dateSeconds = 100
        )
    }

    fun publishRawFolderState(json: String, messageId: Long = 1_000_000) {
        publishFolderState(RemoteFolderState(), messageId)
        folderStateFile!!.writeText(json)
    }

    override suspend fun ensureStorageChat(knownChatId: Long?): Long = TEST_CHAT

    override suspend fun fetchDocuments(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): RemoteDocumentPage {
        onFetch(fromMessageId)
        val newestFirst = (documents + listOfNotNull(folderStateDocument))
            .sortedByDescending { it.messageId }
            .filter { fromMessageId == 0L || it.messageId < fromMessageId }
            .take(limit)
        return RemoteDocumentPage(
            documents = newestFirst,
            nextFromMessageId = newestFirst.lastOrNull()?.messageId ?: 0L
        )
    }

    fun uploadedFolderState(): RemoteFolderState = Json { ignoreUnknownKeys = true }
        .decodeFromString(
            RemoteFolderState.serializer(),
            uploads.last { it.first == RemoteFolderState.FILE_NAME }.second
        )

    override fun uploadDocument(
        chatId: Long,
        localPath: String,
        fileName: String,
        mimeType: String,
        caption: String,
        thumbnailPath: String?
    ): Flow<TelegramUploadEvent> {
        val content = File(localPath).readText()
        uploads += fileName to content
        val messageId = nextMessageId++
        if (fileName == RemoteFolderState.FILE_NAME) {
            publishRawFolderState(content, messageId)
        }
        return flowOf(
            TelegramUploadEvent.Completed(
                RemoteDocument(
                    chatId = chatId,
                    messageId = messageId,
                    remoteFileId = "uploaded-$messageId",
                    uniqueFileId = "uploaded-unique-$messageId",
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = content.length.toLong(),
                    caption = caption,
                    dateSeconds = 100
                )
            )
        )
    }

    override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>) {
        deletedMessages += messageIds
    }

    override suspend fun getDocument(chatId: Long, messageId: Long): RemoteDocument? =
        (documents + listOfNotNull(folderStateDocument)).firstOrNull { it.messageId == messageId }

    override fun messageChanges(chatId: Long): Flow<MessageChange> = changes

    override suspend fun openChat(chatId: Long) {
        openedChats++
    }

    override suspend fun closeChat(chatId: Long) {
        closedChats++
    }

    override fun downloadDocument(remoteFileId: String): Flow<TelegramDownloadEvent> {
        if (failDownloads) return flowOf()
        val file = folderStateFile ?: return flowOf()
        return flowOf(TelegramDownloadEvent.Completed(file.absolutePath, file.length()))
    }

    override suspend fun editCaption(chatId: Long, messageId: Long, caption: String) {
        editedCaptions[messageId] = caption
    }

    fun cleanUp() {
        stateFiles.forEach { it.delete() }
    }
}

internal class FakeStoragePaths : AppStoragePaths by unused() {
    private val root = File.createTempFile("teledrive-paths", "").also {
        it.delete()
        it.mkdirs()
    }
    override val cacheDir: File = File(root, "cache").also { it.mkdirs() }
}

internal class FakeSettings(initial: UserPreferences) : SettingsRepository by unused() {
    private val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        state.value = transform(state.value)
    }
}

internal class CountingScheduler : PublishScheduler {
    var kicks = 0
    override fun kick() {
        kicks++
    }
}

internal inline fun <reified T : Any> unused(): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java)
) { _, method, _ -> throw UnsupportedOperationException(method.name) } as T
