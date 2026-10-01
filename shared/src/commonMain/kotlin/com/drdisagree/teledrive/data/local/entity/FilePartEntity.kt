package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Each part records the plaintext range it covers, so a position maps to its part without
 * downloading others.
 */
@Entity(
    tableName = "file_parts",
    primaryKeys = ["fileId", "partIndex"],
    indices = [Index("fileId"), Index("remoteUniqueId")]
)
data class FilePartEntity(
    val fileId: String,
    val partIndex: Int,
    val chatId: Long?,
    val messageId: Long?,
    val remoteFileId: String?,
    val remoteUniqueId: String?,
    val plainOffset: Long,
    val plainSize: Long,
    val storedSize: Long,
    val uploadedAt: Long
)
