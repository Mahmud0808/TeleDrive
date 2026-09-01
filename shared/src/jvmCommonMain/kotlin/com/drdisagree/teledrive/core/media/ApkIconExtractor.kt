package com.drdisagree.teledrive.core.media

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

object ApkIconExtractor {

    /**
     * Extracts the launcher icon bytes (PNG/WEBP/JPEG) from an APK file.
     * Uses Android PackageManager when available, otherwise searches ZIP entries.
     */
    fun extractIconBytes(file: File): ByteArray? {
        if (!file.exists() || !file.isFile) return null

        // Try Android PackageManager first if running on Android
        val androidBytes = runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = activityThreadClass.getMethod("currentApplication")
            val context = currentAppMethod.invoke(null) as? android.content.Context
            if (context != null) {
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
                val appInfo = info?.applicationInfo
                if (appInfo != null) {
                    appInfo.sourceDir = file.absolutePath
                    appInfo.publicSourceDir = file.absolutePath
                    val drawable = appInfo.loadIcon(pm)
                    if (drawable != null) {
                        val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                            drawable.bitmap
                        } else {
                            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
                            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 192
                            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bmp
                        }
                        ByteArrayOutputStream().use { output ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                            output.toByteArray()
                        }
                    } else null
                } else null
            } else null
        }.getOrNull()

        if (androidBytes != null && androidBytes.isNotEmpty()) {
            return androidBytes
        }

        // Fallback: search ZIP entries
        return runCatching {
            ZipFile(file).use { zip ->
                var bestEntry: java.util.zip.ZipEntry? = null
                var maxScore = -1L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.lowercase()
                    val isImage = name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg") || name.endsWith(".jpeg")
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
}
