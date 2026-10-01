package com.drdisagree.teledrive.data.local.dao

import com.drdisagree.teledrive.domain.model.FileCategory

data class CategoryUsage(
    val category: FileCategory,
    val fileCount: Int,
    val totalBytes: Long
)
