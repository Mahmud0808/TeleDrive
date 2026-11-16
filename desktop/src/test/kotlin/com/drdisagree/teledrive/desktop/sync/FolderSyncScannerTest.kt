package com.drdisagree.teledrive.desktop.sync

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSyncScannerTest {
    @Test
    fun discoversStableFilesAndPreservesSubfolderPaths() {
        val root = Files.createTempDirectory("teledrive-sync-test").toFile()
        try {
            val child = root.resolve("docs").apply { mkdirs() }.resolve("example.txt")
            child.writeText("hello")
            child.setLastModified(System.currentTimeMillis() - 60_000)
            val result = FolderSyncScanner.candidates(root, System.currentTimeMillis()).toList()
            assertEquals(1, result.size)
            assertEquals("docs/example.txt", result.single().relativePath)
            assertEquals("5:${child.lastModified()}", result.single().signature)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresFilesStillBeingWritten() {
        val root = Files.createTempDirectory("teledrive-sync-new").toFile()
        try {
            root.resolve("new.txt").writeText("not ready")
            val result = FolderSyncScanner.candidates(root, System.currentTimeMillis()).toList()
            assertTrue(result.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresMissingDirectory() {
        val root = Files.createTempDirectory("teledrive-sync-missing").toFile()
        root.deleteRecursively()
        assertTrue(FolderSyncScanner.candidates(root, System.currentTimeMillis()).toList().isEmpty())
    }
}
