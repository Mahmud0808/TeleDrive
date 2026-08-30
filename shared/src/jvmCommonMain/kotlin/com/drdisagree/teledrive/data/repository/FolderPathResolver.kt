package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Maps between folder ids and slash-separated paths used in remote manifests.
 * Creating missing segments is serialized to avoid duplicate folders when the
 * sync engine and uploads race.
 */
class FolderPathResolver(
    private val folderDao: FolderDao,
    private val activeChannel: ActiveChannel
) {

    private val creationMutex = Mutex()

    suspend fun pathOf(folderId: String?): String {
        if (folderId == null) return ""
        val segments = ArrayDeque<String>()
        var current = folderDao.byId(folderId)
        var guard = 0
        while (current != null && guard++ < MAX_DEPTH) {
            segments.addFirst(current.name)
            val parent = current.parentId ?: current.preTrashParentId
            current = parent?.let { folderDao.byId(it) }
        }
        return segments.joinToString("/")
    }

    /** Resolves a path without creating anything. Null when it does not exist. */
    suspend fun resolveExisting(path: String): String? {
        if (path.isBlank()) return null
        var parentId: String? = null
        for (segment in path.split('/').filter { it.isNotBlank() }.take(MAX_DEPTH)) {
            val match = folderDao.childrenOf(parentId)
                .firstOrNull { it.name.equals(segment, ignoreCase = true) }
                ?: return null
            parentId = match.id
        }
        return parentId
    }

    /** True when a folder row with this id exists locally. */
    suspend fun exists(folderId: String?): Boolean =
        folderId != null && folderDao.byId(folderId) != null

    /** Resolves a path, creating missing folders. Empty path means root. */
    suspend fun resolveOrCreate(path: String): String? {
        if (path.isBlank()) return null
        return creationMutex.withLock {
            var parentId: String? = null
            for (segment in path.split('/').filter { it.isNotBlank() }.take(MAX_DEPTH)) {
                val existing = folderDao.childrenOf(parentId)
                    .firstOrNull { it.name.equals(segment, ignoreCase = true) }
                parentId = existing?.id ?: run {
                    val now = System.currentTimeMillis()
                    val folder = FolderEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = activeChannel.id(),
                        parentId = parentId,
                        name = segment,
                        createdAt = now,
                        modifiedAt = now
                    )
                    folderDao.upsert(folder)
                    folder.id
                }
            }
            parentId
        }
    }

    companion object {
        private const val MAX_DEPTH = 64
    }
}
