package com.drdisagree.teledrive.presentation.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands local copies to another app. Files still only in Telegram cannot be
 * shared, so callers filter first and tell the user what is missing.
 */
fun shareLocalFiles(
    context: Context,
    paths: List<String>,
    mimeType: String,
    chooserTitle: String
) {
    if (paths.isEmpty()) return
    val uris = ArrayList(
        paths.map { path ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        }
    )
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        Intent.createChooser(intent, chooserTitle)
    )
}
