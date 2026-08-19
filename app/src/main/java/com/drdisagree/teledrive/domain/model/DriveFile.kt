package com.drdisagree.teledrive.domain.model

data class DriveFile(
    val id: String,
    val folderId: String?,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory,
    val localPath: String?,
    val contentHash: String?,
    val chatId: Long?,
    val messageId: Long?,
    val remoteFileId: String?,
    val remoteUniqueId: String?,
    val backupState: BackupState,
    val isHidden: Boolean,
    val isArchived: Boolean,
    val isFavorite: Boolean,
    val isEncrypted: Boolean,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val trashedAt: Long?,
    val createdAt: Long,
    val modifiedAt: Long,
    val addedAt: Long
) {
    val hasLocalCopy: Boolean get() = localPath != null
    val hasRemoteCopy: Boolean get() = messageId != null && chatId != null
    val isTrashed: Boolean get() = trashedAt != null
    val extension: String get() = name.substringAfterLast('.', "")
}
