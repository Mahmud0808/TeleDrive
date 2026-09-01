package com.drdisagree.teledrive.core.media

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal expect fun platformApkIconBytes(file: File): ByteArray?

object ApkIconExtractor {

    /**
     * Extracts the launcher icon bytes from an APK. The platform's package
     * manager renders the true adaptive icon where one exists; elsewhere the
     * best-scoring image entry inside the archive stands in.
     */
    fun extractIconBytes(file: File): ByteArray? {
        if (!file.exists() || !file.isFile) return null
        platformApkIconBytes(file)?.takeIf { it.isNotEmpty() }?.let { return it }
        return zipEntryIconBytes(file)
    }

    private fun zipEntryIconBytes(file: File): ByteArray? = runCatching {
        ZipFile(file).use { zip ->
            var bestEntry: ZipEntry? = null
            var maxScore = -1L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name.lowercase()
                val isImage = name.endsWith(".png") || name.endsWith(".webp") ||
                        name.endsWith(".jpg") || name.endsWith(".jpeg")
                if (!isImage) continue

                val priority = when {
                    name.contains("ic_launcher") -> 100
                    name.contains("app_icon") -> 50
                    name.contains("icon") -> 20
                    name.contains("res/mipmap") || name.contains("res/drawable") -> 10
                    else -> 1
                }
                val score = priority * 1_000_000L + entry.size
                if (score > maxScore) {
                    maxScore = score
                    bestEntry = entry
                }
            }
            bestEntry?.let { entry ->
                zip.getInputStream(entry).use { stream -> stream.readBytes() }
            }
        }
    }.getOrNull()
}
