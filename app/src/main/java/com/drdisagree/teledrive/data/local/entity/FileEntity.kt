package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory

@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("folderId"),
        Index("name"),
        Index("category"),
        Index("backupState"),
        Index("trashedAt"),
        Index("remoteUniqueId"),
        Index(value = ["localPath"], unique = false),
        Index(value = ["contentHash"])
    ]
)
data class FileEntity(
    @PrimaryKey val id: String,
    val folderId: String?,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory,
    /** Absolute path of the local copy, null when the file exists only remotely. */
    val localPath: String?,
    /** SHA-256 of local content, used for duplicate and incremental backup detection. */
    val contentHash: String?,
    val chatId: Long?,
    val messageId: Long?,
    val remoteFileId: String?,
    val remoteUniqueId: String?,
    val backupState: BackupState,
    val isHidden: Boolean = false,
    val isArchived: Boolean = false,
    val isFavorite: Boolean = false,
    val isEncrypted: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val trashedAt: Long? = null,
    /** Folder the file lived in before being trashed, for restore. */
    val preTrashFolderId: String? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val addedAt: Long
)
