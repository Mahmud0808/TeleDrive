package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Carried in the folder document so other devices drop the folder instead of uploading it back. */
@Entity(tableName = "folder_tombstones")
data class FolderTombstoneEntity(
    @PrimaryKey val id: String,
    val chatId: Long?,
    val deletedAt: Long
)
