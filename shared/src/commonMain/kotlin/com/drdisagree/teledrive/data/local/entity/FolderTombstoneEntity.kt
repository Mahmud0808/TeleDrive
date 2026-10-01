package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A folder deleted for good. The folder document carries these so another
 * device removes its copy instead of uploading the folder back.
 */
@Entity(tableName = "folder_tombstones")
data class FolderTombstoneEntity(
    @PrimaryKey val id: String,
    val chatId: Long?,
    val deletedAt: Long
)
