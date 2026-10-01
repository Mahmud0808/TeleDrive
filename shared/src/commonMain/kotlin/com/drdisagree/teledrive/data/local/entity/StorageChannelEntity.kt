package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Files and folders carry the owning chatId, so several drives share one database. */
@Entity(tableName = "storage_channels")
data class StorageChannelEntity(
    @PrimaryKey val chatId: Long,
    val title: String,
    val backupFolders: String = "",
    val photoPath: String? = null,
    /** Set once the starting exclusions were added, so removals stay removed. */
    val defaultsSeeded: Boolean = false,
    val remoteFileCount: Int = 0,
    val addedAt: Long,
    val lastOpenedAt: Long
)
