package com.drdisagree.teledrive.core.transfer

data class UploadedPiece(
    val chatId: Long,
    val messageId: Long,
    val plainOffset: Long,
    val plainSize: Long,
    val storedSize: Long
)
