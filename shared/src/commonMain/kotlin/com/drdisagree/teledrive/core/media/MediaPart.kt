package com.drdisagree.teledrive.core.media

data class MediaPart(
    val remoteFileId: String,
    val plainOffset: Long,
    val plainSize: Long
)
