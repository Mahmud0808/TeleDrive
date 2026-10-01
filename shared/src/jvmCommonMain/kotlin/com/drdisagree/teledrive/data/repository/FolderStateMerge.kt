package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState.Entry
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState.Tombstone

/**
 * Combines this device's folder tree with the one in the channel, folder by
 * folder, so a device that has not caught up cannot erase changes made
 * elsewhere. The newer clock wins, and a deletion beats any copy that is not
 * newer than it. A folder changed after its deletion survives and the
 * deletion is dropped.
 */
internal object FolderStateMerge {

    fun merge(
        local: List<Entry>,
        localDeleted: List<Tombstone>,
        remote: RemoteFolderState?,
        keepDeletionsSince: Long
    ): RemoteFolderState {
        val newest = LinkedHashMap<String, Entry>()
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
