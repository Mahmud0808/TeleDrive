package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Telegram channel used as a drive. Rows in files and folders carry the
 * chatId of the channel that owns them, so several drives can share one local
 * database while only the active one is ever shown.
 */
@Entity(tableName = "storage_channels")
data class StorageChannelEntity(
    @PrimaryKey val chatId: Long,
    val title: String,
    val backupFolders: String = "",
    val photoPath: String? = null,
    /** Set once the starting exclusions were added, so removals stay removed. */
    val defaultsSeeded: Boolean = false,
    /** Documents Telegram reports in the channel, known before indexing. */
    val remoteFileCount: Int = 0,
    val addedAt: Long,
    val lastOpenedAt: Long
)
