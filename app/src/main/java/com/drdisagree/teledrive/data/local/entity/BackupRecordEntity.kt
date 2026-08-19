package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per source path that has ever been backed up. Used for incremental
 * backup decisions: a file is re-uploaded only when size or hash changed.
 */
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
