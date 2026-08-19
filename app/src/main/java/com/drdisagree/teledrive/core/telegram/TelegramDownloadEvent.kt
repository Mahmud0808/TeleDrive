package com.drdisagree.teledrive.core.telegram

sealed interface TelegramDownloadEvent {

    data class Progress(val transferredBytes: Long, val totalBytes: Long) : TelegramDownloadEvent

    /** [localPath] points into TDLib's managed file directory. */
    data class Completed(val localPath: String, val sizeBytes: Long) : TelegramDownloadEvent
}
