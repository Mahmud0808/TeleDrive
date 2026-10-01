package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderEntry
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderTombstone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderStateMergeTest {

    @Test
    fun `a device that has not caught up cannot overwrite a newer rename`() {
        val merged = merge(
            local = listOf(entry("x", "Old", clock = 1_000)),
            remote = RemoteFolderState(folders = listOf(entry("x", "Renamed", clock = 2_000)))
        )

        assertEquals("Renamed", merged.byId("x").name)
    }

    @Test
    fun `the newer local change wins`() {
        val merged = merge(
            local = listOf(entry("x", "Mine", clock = 3_000)),
            remote = RemoteFolderState(folders = listOf(entry("x", "Theirs", clock = 2_000)))
        )

        assertEquals("Mine", merged.byId("x").name)
    }

    @Test
    fun `folders created on either side are both kept`() {
        val merged = merge(
            local = listOf(entry("mine", "Mine")),
            remote = RemoteFolderState(folders = listOf(entry("theirs", "Theirs")))
        )

        assertEquals(setOf("mine", "theirs"), merged.folders.map { it.id }.toSet())
    }

    @Test
    fun `a deletion removes every copy that is not newer`() {
        val merged = merge(
            local = listOf(entry("x", "Work", clock = 1_000)),
            remote = RemoteFolderState(deleted = listOf(RemoteFolderTombstone("x", 2_000)))
        )

        assertTrue(merged.folders.none { it.id == "x" })
        assertEquals(listOf(RemoteFolderTombstone("x", 2_000)), merged.deleted)
    }

    @Test
    fun `a folder changed after its deletion survives and the deletion is dropped`() {
        val merged = merge(
            local = listOf(entry("x", "Work", clock = 3_000)),
            remote = RemoteFolderState(deleted = listOf(RemoteFolderTombstone("x", 2_000)))
        )

        assertEquals("Work", merged.byId("x").name)
        assertTrue(merged.deleted.isEmpty())
    }

    @Test
    fun `a local deletion removes the remote copy`() {
        val merged = merge(
            local = emptyList(),
            localDeleted = listOf(RemoteFolderTombstone("x", 2_000)),
            remote = RemoteFolderState(folders = listOf(entry("x", "Work", clock = 1_000)))
        )

        assertTrue(merged.folders.isEmpty())
        assertEquals(listOf(RemoteFolderTombstone("x", 2_000)), merged.deleted)
    }

    @Test
    fun `the latest copy of a deletion is kept`() {
        val merged = merge(
            local = emptyList(),
            localDeleted = listOf(RemoteFolderTombstone("x", 1_000)),
            remote = RemoteFolderState(deleted = listOf(RemoteFolderTombstone("x", 4_000)))
        )

        assertEquals(listOf(RemoteFolderTombstone("x", 4_000)), merged.deleted)
    }

    @Test
    fun `deletions past the retention window are forgotten`() {
        val merged = merge(
            local = emptyList(),
            remote = RemoteFolderState(deleted = listOf(RemoteFolderTombstone("old", 10), RemoteFolderTombstone("new", 5_000))),
            keepDeletionsSince = 1_000
        )

        assertEquals(listOf("new"), merged.deleted.map { it.id })
    }

    @Test
    fun `a newer child of a deleted folder moves to the root`() {
        val merged = merge(
            local = listOf(entry("child", "Kept", parentId = "parent", clock = 3_000)),
            remote = RemoteFolderState(
                folders = listOf(entry("parent", "Gone", clock = 1_000)),
                deleted = listOf(RemoteFolderTombstone("parent", 2_000))
            )
        )

        assertNull(merged.byId("child").parentId)
        assertTrue(merged.folders.none { it.id == "parent" })
    }

    @Test
    fun `a folder document from an older app version merges by modifiedAt`() {
        val merged = merge(
            local = listOf(entry("x", "Mine", clock = 1_000)),
            remote = RemoteFolderState(
                folders = listOf(
                    RemoteFolderEntry(id = "x", name = "Theirs", createdAt = 1, modifiedAt = 2_000)
                )
            )
        )

        assertEquals("Theirs", merged.byId("x").name)
    }

    @Test
    fun `nothing in the channel leaves the local tree as it is`() {
        val local = listOf(entry("a", "A"), entry("b", "B", parentId = "a"))

        val merged = merge(local = local, remote = null)

        assertEquals(local, merged.folders)
        assertTrue(merged.deleted.isEmpty())
    }

    private fun merge(
        local: List<RemoteFolderEntry>,
        remote: RemoteFolderState?,
        localDeleted: List<RemoteFolderTombstone> = emptyList(),
        keepDeletionsSince: Long = 0
    ) = FolderStateMerge.merge(local, localDeleted, remote, keepDeletionsSince)

    private fun RemoteFolderState.byId(id: String) = folders.single { it.id == id }

    private fun entry(
        id: String,
        name: String,
        parentId: String? = null,
        clock: Long = 1_000
    ) = RemoteFolderEntry(
        id = id,
        parentId = parentId,
        name = name,
        createdAt = 1,
        modifiedAt = clock,
        changedAt = clock
    )
}
