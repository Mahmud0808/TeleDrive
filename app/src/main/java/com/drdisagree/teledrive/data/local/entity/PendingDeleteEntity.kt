package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity

/**
 * A permanent delete that Telegram has not confirmed yet. Written before the
 * message is removed so a crash mid-delete leaves a record to replay, instead
 * of a local row pointing at a message that is already gone.
 */
@Entity(tableName = "pending_deletes", primaryKeys = ["chatId", "messageId"])
data class PendingDeleteEntity(
    val chatId: Long,
    val messageId: Long,
    val fileId: String
)
