package com.drdisagree.teledrive.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderCycleCheckTest {

    @Test
    fun `a folder cannot become its own parent`() {
        assertTrue(
            FolderCycleCheck.createsCycle(
                folderId = "photos",
                targetParentId = "photos",
                targetAncestors = listOf("photos")
            )
        )
    }

    @Test
    fun `a folder cannot move into its own descendant`() {
        assertTrue(
            FolderCycleCheck.createsCycle(
                folderId = "photos",
                targetParentId = "2026",
                targetAncestors = listOf("2026", "trips", "photos")
            )
        )
    }

    @Test
    fun `moving to an unrelated branch is allowed`() {
        assertFalse(
            FolderCycleCheck.createsCycle(
                folderId = "photos",
                targetParentId = "documents",
                targetAncestors = listOf("documents", "work")
            )
        )
    }

    @Test
    fun `moving to the drive root is allowed`() {
        assertFalse(
            FolderCycleCheck.createsCycle(
                folderId = "photos",
                targetParentId = null,
                targetAncestors = emptyList()
            )
        )
    }
}
