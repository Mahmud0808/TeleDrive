package com.drdisagree.teledrive.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import java.io.ByteArrayOutputStream
import java.io.File

/* ActivityThread is hidden API, so the application context is reached through
   reflection: this extractor runs from shared code with no context to inject. */
@Suppress("DiscouragedPrivateApi")
internal actual fun platformApkIconBytes(file: File): ByteArray? = runCatching {
    val context = Class.forName("android.app.ActivityThread")
        .getMethod("currentApplication")
        .invoke(null) as? Context
        ?: return null
    val packageManager = context.packageManager
    val info = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
    val appInfo = info?.applicationInfo ?: return null
    appInfo.sourceDir = file.absolutePath
    appInfo.publicSourceDir = file.absolutePath
    val drawable = appInfo.loadIcon(packageManager) ?: return null

    val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
        drawable.bitmap
    } else {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { rendered ->
            val canvas = Canvas(rendered)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        }
    }
    ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}.getOrNull()

private const val FALLBACK_ICON_SIZE = 192
