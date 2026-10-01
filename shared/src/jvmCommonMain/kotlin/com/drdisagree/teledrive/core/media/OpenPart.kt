package com.drdisagree.teledrive.core.media

internal class OpenPart(
    val part: MediaPart,
    val index: Int,
    val fileId: Int,
    val storedSize: Long,
    val salt: ByteArray?
)
