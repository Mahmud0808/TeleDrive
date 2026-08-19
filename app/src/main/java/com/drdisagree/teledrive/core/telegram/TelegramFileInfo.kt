package com.drdisagree.teledrive.core.telegram

/**
 * Snapshot of a TDLib file's transfer state, used by the streaming data source.
 * [fileId] is TDLib's session-scoped integer id.
 */
data class TelegramFileInfo(
    val fileId: Int,
    val sizeBytes: Long,
    val localPath: String?,
    val isDownloadingCompleted: Boolean,
    val downloadOffset: Long,
    val downloadedPrefixSize: Long
)
