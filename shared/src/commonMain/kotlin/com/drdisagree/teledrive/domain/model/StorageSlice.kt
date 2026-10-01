package com.drdisagree.teledrive.domain.model

data class StorageSlice(
    val category: FileCategory,
    val fileCount: Int,
    val totalBytes: Long
)
