package com.drdisagree.teledrive.data.mapper

import com.drdisagree.teledrive.data.local.dao.FolderWithCount
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.domain.model.DriveFolder

fun FolderEntity.toDomain(): DriveFolder = DriveFolder(
    id = id,
    parentId = parentId,
    name = name,
    isHidden = isHidden,
    isArchived = isArchived,
    isFavorite = isFavorite,
    trashedAt = trashedAt,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

fun FolderWithCount.toDomain(): DriveFolder = folder.toDomain().copy(
    fileCount = fileCount,
    folderCount = folderCount
)
