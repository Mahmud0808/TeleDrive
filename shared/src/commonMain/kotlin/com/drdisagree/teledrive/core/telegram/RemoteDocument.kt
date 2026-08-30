package com.drdisagree.teledrive.core.telegram

/**
 * A document message in the storage chat, expressed without TDLib types.
 * [remoteFileId] can change between sessions; [uniqueFileId] is stable and is
 * used as the durable remote identity.
 */
data class RemoteDocument(
    val chatId: Long,
    val messageId: Long,
    val remoteFileId: String,
    val uniqueFileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val caption: String,
    val dateSeconds: Int,
    val miniThumbnail: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean =
        other is RemoteDocument && other.chatId == chatId && other.messageId == messageId

    override fun hashCode(): Int = 31 * chatId.hashCode() + messageId.hashCode()
}
