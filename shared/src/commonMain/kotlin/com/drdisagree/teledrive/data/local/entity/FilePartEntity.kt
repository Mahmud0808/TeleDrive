package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One piece of a file too large for a single Telegram message. Parts are whole
 * files in the channel, named with a .001 style suffix, and each carries the
 * plaintext range it covers so a reader can map a position onto the part
 * holding it without downloading anything else.
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
