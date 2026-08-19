package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "thumbnails",
    indices = [Index("lastAccessAt")]
)
data class ThumbnailEntity(
    @PrimaryKey val fileId: String,
    val path: String,
    val encrypted: Boolean,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val generatedAt: Long,
    val lastAccessAt: Long
)
