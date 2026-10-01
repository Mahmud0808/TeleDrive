package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity

/** Written before the message is removed, so a crash mid-delete leaves a record to replay. */
@Entity(tableName = "pending_deletes", primaryKeys = ["chatId", "messageId"])
data class PendingDeleteEntity(
    val chatId: Long,
    val messageId: Long,
    val fileId: String
)
