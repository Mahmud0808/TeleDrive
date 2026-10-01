package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderEntry
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderTombstone

/** Per folder the newer clock wins; a deletion beats any copy that is not newer than it. */
internal object FolderStateMerge {

    fun merge(
        local: List<RemoteFolderEntry>,
        localDeleted: List<RemoteFolderTombstone>,
        remote: RemoteFolderState?,
        keepDeletionsSince: Long
    ): RemoteFolderState {
        val newest = LinkedHashMap<String, RemoteFolderEntry>()
        remote?.folders.orEmpty().forEach { newest[it.id] = it }
        local.forEach { entry ->
            val other = newest[entry.id]
            if (other == null || entry.clock >= other.clock) newest[entry.id] = entry
        }

        val deletions = (localDeleted + remote?.deleted.orEmpty())
            .groupBy { it.id }
            .map { (_, copies) -> copies.maxBy { it.deletedAt } }
            .filter { it.deletedAt >= keepDeletionsSince }
            .filter { deletion -> newest[deletion.id]?.let { it.clock <= deletion.deletedAt } ?: true }
        val deletedIds = deletions.map { it.id }.toSet()

        val alive = newest.values.filter { it.id !in deletedIds }
        val aliveIds = alive.map { it.id }.toSet()
        val folders = alive.map { entry ->
            if (entry.parentId != null && entry.parentId !in aliveIds) entry.copy(parentId = null) else entry
        }
        return RemoteFolderState(folders = folders, deleted = deletions)
    }
}
