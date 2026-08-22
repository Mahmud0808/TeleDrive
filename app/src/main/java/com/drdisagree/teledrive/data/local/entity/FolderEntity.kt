package com.drdisagree.teledrive.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentId"), Index("name"), Index("chatId")]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val chatId: Long?,
    val parentId: String?,
    val name: String,
    val isHidden: Boolean = false,
    val isArchived: Boolean = false,
    val isFavorite: Boolean = false,
    val trashedAt: Long? = null,
    val preTrashParentId: String? = null,
    val pendingPublish: Boolean = false,
    val createdAt: Long,
    val modifiedAt: Long
)
