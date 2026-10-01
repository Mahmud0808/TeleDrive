package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_records",
    indices = [Index(value = ["sourcePath"], unique = true), Index("contentHash")]
)
data class BackupRecordEntity(
    @PrimaryKey val id: String,
    val sourcePath: String,
    val fileId: String?,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val contentHash: String?,
    val backedUpAt: Long
)
