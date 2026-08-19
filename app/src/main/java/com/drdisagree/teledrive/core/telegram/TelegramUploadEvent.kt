package com.drdisagree.teledrive.core.telegram

sealed interface TelegramUploadEvent {

    data class Started(val temporaryMessageId: Long) : TelegramUploadEvent

    data class Progress(val transferredBytes: Long, val totalBytes: Long) : TelegramUploadEvent

    data class Completed(val document: RemoteDocument) : TelegramUploadEvent
}
