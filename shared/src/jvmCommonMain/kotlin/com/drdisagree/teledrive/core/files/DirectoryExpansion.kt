package com.drdisagree.teledrive.core.files

import java.io.File

/**
 * Lists the files under a picked directory, keeping each one's folder path
 * relative to the directory's parent so the picked folder itself becomes the
 * first segment. Returns null when the reference is not a readable directory.
 */
fun expandDirectory(reference: String): List<ImportSource>? {
    val root = runCatching { File(reference) }.getOrNull() ?: return null
    if (!root.isDirectory) return null
    val base = root.parentFile ?: return null
    return root.walkTopDown()
        .filter { it.isFile && it.length() > 0 }
        .map { file ->
            ImportSource(
                reference = file.absolutePath,
                relativeFolder = file.parentFile
                    ?.relativeToOrNull(base)
                    ?.invariantSeparatorsPath
                    .orEmpty()
            )
        }
        .toList()
}
