package com.drdisagree.teledrive.domain.model

data class DriveFolder(
    val id: String,
    val parentId: String?,
    val name: String,
    val isHidden: Boolean,
    val isArchived: Boolean,
    val isFavorite: Boolean,
    val trashedAt: Long?,
    val createdAt: Long,
    val modifiedAt: Long,
    val fileCount: Int = 0,
    val folderCount: Int = 0
) {
    val isTrashed: Boolean get() = trashedAt != null
}
