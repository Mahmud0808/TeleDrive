package com.drdisagree.teledrive.domain.repository

data class CacheStats(
    val thumbnailBytes: Long,
    val previewBytes: Long,
    val streamBytes: Long,
    val tempBytes: Long,
    val tdlibBytes: Long
) {
    val totalBytes: Long
        get() = thumbnailBytes + previewBytes + streamBytes + tempBytes + tdlibBytes
}
