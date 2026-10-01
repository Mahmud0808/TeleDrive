package com.drdisagree.teledrive.core.telegram

import com.drdisagree.teledrive.domain.model.Country
import com.drdisagree.teledrive.domain.model.LinkMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** No TDLib types cross this boundary. */
interface TelegramClient {

    val authState: StateFlow<TelegramAuthState>
    val connectionState: StateFlow<TelegramConnectionState>

    /** Safe to call repeatedly. */
    suspend fun start(credentials: TelegramCredentials)

    /** Scanning every chat is skipped once a name search finds all of [knownChatIds]. */
    suspend fun listStorageChannels(knownChatIds: List<Long> = emptyList()): List<StorageChannel>

    suspend fun createStorageChannel(label: String): StorageChannel

    /** Replaces a message's document in place, keeping its id and manifest. */
    suspend fun editDocument(
        chatId: Long,
        messageId: Long,
        localPath: String,
        caption: String
    ): RemoteDocument

    /** Link metadata fetched by Telegram, so the device never calls the site. */
    suspend fun linkPreview(url: String, withImage: Boolean): LinkMetadata?

    suspend fun renameStorageChannel(chatId: Long, label: String): String

    /** Deletes the channel and everything in it, for every member. */
    suspend fun deleteStorageChannel(chatId: Long)

    /** True when the chat is reachable, false when gone, null when unclear. */
    suspend fun chatExists(chatId: Long): Boolean?

    /**
     * Null when the channel has no picture; failures throw so callers can tell that apart from
     * unreachable.
     */
    suspend fun fetchChannelPhoto(chatId: Long): String?

    suspend fun countries(): List<Country>

    suspend fun detectedCountryCode(): String?

    suspend fun submitPhoneNumber(phoneNumber: String)

    suspend fun requestQrCodeAuthentication()

    /** TDLib offers no way back from QR login, so the client is closed and started again. */
    suspend fun restartAuthentication()

    suspend fun submitEmailAddress(email: String)

    suspend fun submitEmailCode(code: String)

    suspend fun submitCode(code: String)

    suspend fun submitPassword(password: String)

    suspend fun resendCode()

    suspend fun logout()

    suspend fun getCurrentUser(): TelegramUser

    suspend fun getLimits(): TelegramLimits

    suspend fun applyProxy(proxy: TelegramProxy?)

    suspend fun testProxy(proxy: TelegramProxy)

    suspend fun reconnect()

    /**
     * Finds the channel again by its marker after a local wipe, creating one only when none exists.
     */
    suspend fun ensureStorageChat(knownChatId: Long?): Long

    /** Cancelling the collection aborts the upload and deletes the pending message. */
    fun uploadDocument(
        chatId: Long,
        localPath: String,
        fileName: String,
        mimeType: String,
        caption: String,
        thumbnailPath: String? = null
    ): Flow<TelegramUploadEvent>

    /** Telegram stores one copy server side, so this costs no upload bandwidth. */
    suspend fun copyDocument(
        chatId: Long,
        remoteFileId: String,
        fileName: String,
        mimeType: String,
        caption: String
    ): RemoteDocument

    /** Cancelling the collection cancels the download. */
    fun downloadDocument(remoteFileId: String): Flow<TelegramDownloadEvent>

    /** Telegram's own generated preview, for files with no local copy; null when there is none. */
    suspend fun fetchThumbnail(chatId: Long, messageId: Long): ByteArray?

    suspend fun resolveFile(remoteFileId: String): TelegramFileInfo

    suspend fun getFileInfo(fileId: Int): TelegramFileInfo

    /** Non-blocking; re-targets a download already in progress. */
    suspend fun requestFileRange(fileId: Int, offset: Long, limit: Long)

    suspend fun readFilePart(fileId: Int, offset: Long, count: Long): ByteArray

    suspend fun cancelFileDownload(fileId: Int)

    fun fileUpdates(fileId: Int): Flow<TelegramFileInfo>

    suspend fun fetchDocuments(chatId: Long, fromMessageId: Long, limit: Int): RemoteDocumentPage

    suspend fun getDocument(chatId: Long, messageId: Long): RemoteDocument?

    suspend fun editCaption(chatId: Long, messageId: Long, caption: String)

    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>)

    /**
     * Leaves out this session's own sends, edits and deletions, unsent messages and cache-only
     * deletions.
     */
    fun messageChanges(chatId: Long): Flow<MessageChange>

    /** Telegram only pushes a channel's changes reliably while it is open. */
    suspend fun openChat(chatId: Long)

    suspend fun closeChat(chatId: Long)
}
