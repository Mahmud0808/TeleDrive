package com.drdisagree.teledrive.data.local.dao

import com.drdisagree.teledrive.domain.model.FileCategory

/** Row of the storage-by-type rollup. */
data class CategoryUsage(
    val category: FileCategory,
    val fileCount: Int,
    val totalBytes: Long
)
