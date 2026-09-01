package com.drdisagree.teledrive.core.media

import java.io.File
import java.util.zip.ZipFile

object ApkIconExtractor {

    /**
     * Extracts the launcher icon bytes (PNG/WEBP/JPEG) from an APK file.
     * Searches ZIP entries for launcher icon files and picks the highest resolution one.
     */
    fun extractIconBytes(file: File): ByteArray? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            ZipFile(file).use { zip ->
                var bestEntry: java.util.zip.ZipEntry? = null
                var maxScore = -1L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.lowercase()
                    if ((name.contains("ic_launcher") || name.contains("app_icon") || name.contains("icon")) &&
                        (name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
                    ) {
                        val priority = when {
                            name.contains("ic_launcher") -> 100
                            name.contains("app_icon") -> 50
                            else -> 10
                        }
                        val score = priority * 1_000_000L + entry.size
                        if (score > maxScore) {
                            maxScore = score
                            bestEntry = entry
                        }
                    }
                }
                bestEntry?.let { entry ->
                    zip.getInputStream(entry).use { stream -> stream.readBytes() }
                }
            }
        }.getOrNull()
    }
}
