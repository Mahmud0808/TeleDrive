package com.drdisagree.teledrive.data.mapper

import com.drdisagree.teledrive.data.local.entity.BackupSessionEntity
import com.drdisagree.teledrive.domain.model.BackupSession

fun BackupSessionEntity.toDomain(): BackupSession = BackupSession(
    id = id,
    trigger = trigger,
    status = status,
    totalFiles = totalFiles,
    completedFiles = completedFiles,
    failedFiles = failedFiles,
    skippedFiles = skippedFiles,
    totalBytes = totalBytes,
    transferredBytes = transferredBytes,
    startedAt = startedAt,
    completedAt = completedAt
)
