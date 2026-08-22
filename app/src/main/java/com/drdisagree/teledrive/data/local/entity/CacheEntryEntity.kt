package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cache_entries",
    indices = [Index("lastAccessAt"), Index("type")]
)
data class CacheEntryEntity(
    @PrimaryKey val path: String,
    val fileId: String?,
    val type: CacheEntryType,
    val sizeBytes: Long,
    val encrypted: Boolean,
    val createdAt: Long,
    val lastAccessAt: Long
)
