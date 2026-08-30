package com.drdisagree.teledrive.data.local.dao

import androidx.room.Embedded
import com.drdisagree.teledrive.data.local.entity.FolderEntity

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val fileCount: Int,
    val folderCount: Int
)
