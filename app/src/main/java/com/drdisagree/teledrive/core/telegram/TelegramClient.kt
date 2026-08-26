package com.drdisagree.teledrive.core.telegram

import com.drdisagree.teledrive.domain.model.Country
import com.drdisagree.teledrive.domain.model.LinkMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Telegram abstraction used by the rest of the app. No TDLib types cross this
 * boundary, so the implementation can be replaced or upgraded independently.
 */
interface TelegramClient {

    val authState: StateFlow<TelegramAuthState>
    val connectionState: StateFlow<TelegramConnectionState>

    /** Starts TDLib with the given credentials. Safe to call repeatedly. */
    suspend fun start(credentials: TelegramCredentials)

    /**
     * Channels on this account that can serve as the drive. Scanning every chat
     * is skipped when searching by name already finds [knownChatIds] of them.
     */
    suspend fun listStorageChannels(knownChatIds: List<Long> = emptyList()): List<StorageChannel>

    /**
     * Creates another drive channel. [label] is appended to the drive name so
     * several channels stay distinguishable in Telegram and in the app.
     */
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

    /** Renames a drive channel, keeping the name recognizable as a drive. */
    suspend fun renameStorageChannel(chatId: Long, label: String): String

    /** Deletes the channel and everything in it, for every member. */
    suspend fun deleteStorageChannel(chatId: Long)

    /** True when the chat is reachable, false when gone, null when unclear. */
    suspend fun chatExists(chatId: Long): Boolean?

    /** Downloads the channel picture, returning its local path or null. */
    suspend fun fetchChannelPhoto(chatId: Long): String?

    /** Dialing codes as Telegram lists them, localized by TDLib. */
    suspend fun countries(): List<Country>

    /** ISO code Telegram infers for this connection, or null when unknown. */
    suspend fun detectedCountryCode(): String?

    suspend fun submitPhoneNumber(phoneNumber: String)

    /** Sets the login email for accounts that sign in that way. */
    /** Switches login to a QR code an authorized Telegram app scans. */
    suspend fun requestQrCodeAuthentication()

    /**
     * Leaves QR login and returns to the phone form. TDLib refuses a phone
     * number while it waits for another device, and offers no way back, so the
     * client is closed and started again.
     */
    suspend fun restartAuthentication()

    suspend fun submitEmailAddress(email: String)

    suspend fun submitEmailCode(code: String)

    suspend fun submitCode(code: String)

    suspend fun submitPassword(password: String)

    suspend fun resendCode()

    suspend fun logout()

    suspend fun getCurrentUser(): TelegramUser

    suspend fun getLimits(): TelegramLimits

    /** Routes traffic through [proxy], or connects directly when null. */
    suspend fun applyProxy(proxy: TelegramProxy?)

    /** Throws [TelegramException] when Telegram cannot be reached through [proxy]. */
    suspend fun testProxy(proxy: TelegramProxy)

    /**
     * Returns the chat id of the private storage channel, validating
     * [knownChatId] first, then searching existing chats by marker, and
     * finally creating a new channel. Survives a local data wipe.
     */
    suspend fun ensureStorageChat(knownChatId: Long?): Long

    /**
     * Uploads a local file as a document. Collecting the returned flow drives
     * the upload; cancelling the collection aborts it and deletes the pending
     * message.
     */
    fun uploadDocument(
        chatId: Long,
        localPath: String,
        fileName: String,
        mimeType: String,
        caption: String,
        thumbnailPath: String? = null
    ): Flow<TelegramUploadEvent>

    /**
     * Creates a second message referencing an already-uploaded file. Telegram
     * stores one copy server-side, so this costs no upload bandwidth.
     */
    suspend fun copyDocument(
        chatId: Long,
        remoteFileId: String,
        fileName: String,
        mimeType: String,
        caption: String
    ): RemoteDocument

    /**
     * Downloads a full remote file into TDLib's managed storage. Cancelling
     * the collection cancels the download.
     */
    fun downloadDocument(remoteFileId: String): Flow<TelegramDownloadEvent>

    /**
     * Downloads Telegram's own generated preview for a document, so a file
     * with no local copy can still show a thumbnail. Null when the message
     * carries no preview.
     */
    suspend fun fetchThumbnail(chatId: Long, messageId: Long): ByteArray?

    /** Resolves a stable remote file id to a session file id for streaming. */
    suspend fun resolveFile(remoteFileId: String): TelegramFileInfo

    suspend fun getFileInfo(fileId: Int): TelegramFileInfo

    /** Starts or re-targets a partial download; non-blocking. */
    suspend fun requestFileRange(fileId: Int, offset: Long, limit: Long)

    /** Reads bytes already downloaded for [fileId]. */
    suspend fun readFilePart(fileId: Int, offset: Long, count: Long): ByteArray

    suspend fun cancelFileDownload(fileId: Int)

    /** Emits file state changes for [fileId], used to wait for buffered data. */
    fun fileUpdates(fileId: Int): Flow<TelegramFileInfo>

    suspend fun fetchDocuments(chatId: Long, fromMessageId: Long, limit: Int): RemoteDocumentPage

    suspend fun getDocument(chatId: Long, messageId: Long): RemoteDocument?

    suspend fun editCaption(chatId: Long, messageId: Long, caption: String)

    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>)
}
