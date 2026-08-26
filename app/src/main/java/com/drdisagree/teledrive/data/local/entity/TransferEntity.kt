package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.drdisagree.teledrive.domain.model.TransferStage
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType

@Entity(
    tableName = "transfers",
    indices = [Index("state"), Index("fileId"), Index("createdAt")]
)
data class TransferEntity(
    @PrimaryKey val id: String,
    val type: TransferType,
    val fileId: String?,
    val displayName: String,
    val localPath: String?,
    val chatId: Long?,
    val messageId: Long?,
    val remoteFileId: String?,
    val telegramFileId: Int? = null,
    val sizeBytes: Long,
    val transferredBytes: Long = 0,
    val state: TransferState,
    val priority: Int = 0,
    val errorMessage: String? = null,
    val backupSessionId: String? = null,
    val speedBytesPerSecond: Long = 0,
    val stage: TransferStage? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)
