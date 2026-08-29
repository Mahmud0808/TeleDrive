package com.drdisagree.teledrive.domain.model

/** How much of the drive one file category takes up. */
data class StorageSlice(
    val category: FileCategory,
    val fileCount: Int,
    val totalBytes: Long
)
