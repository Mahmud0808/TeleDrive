package com.drdisagree.teledrive.core.telegram

/** [fileId] is TDLib's session-scoped id. */
data class TelegramFileInfo(
    val fileId: Int,
    val sizeBytes: Long,
    val localPath: String?,
    val isDownloadingCompleted: Boolean,
    val isDownloadingActive: Boolean,
    val downloadOffset: Long,
    val downloadedPrefixSize: Long
)
