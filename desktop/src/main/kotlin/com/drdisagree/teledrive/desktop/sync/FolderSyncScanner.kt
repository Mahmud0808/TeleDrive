package com.drdisagree.teledrive.desktop.sync

import java.io.File
import java.nio.file.Files

/**
 * Stable, regular files only. Symbolic links are excluded so selecting a folder
 * cannot accidentally upload content from outside that folder.
 */
internal object FolderSyncScanner {
    data class Candidate(
        val file: File,
        val relativePath: String,
        val signature: String
    )

    fun candidates(root: File, nowMillis: Long, settleMillis: Long = 5_000L): Sequence<Candidate> {
        if (!root.isDirectory || !root.canRead()) return emptySequence()
        return root.walkTopDown()
            .onEnter { !Files.isSymbolicLink(it.toPath()) }
            .asSequence()
            .filter { it.isFile && it.canRead() && !Files.isSymbolicLink(it.toPath()) }
            .filter { it.lastModified() > 0L && nowMillis - it.lastModified() >= settleMillis }
            .map { file ->
                Candidate(
                    file = file,
                    relativePath = file.relativeTo(root).invariantSeparatorsPath,
                    signature = "${file.length()}:${file.lastModified()}"
                )
            }
    }
}
