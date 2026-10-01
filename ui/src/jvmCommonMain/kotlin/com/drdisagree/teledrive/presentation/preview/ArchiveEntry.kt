package com.drdisagree.teledrive.presentation.preview

data class ArchiveEntry(
    val name: String,
    val sizeBytes: Long,
    val compressedBytes: Long,
    val isDirectory: Boolean
)
