package com.drdisagree.teledrive.core.files

import android.content.Context
import java.io.File

/** Local copies of notes, kept beside the app's other staged files. */
class NoteStore(
    private val context: Context
) {

    fun write(name: String, body: String, existingPath: String?): File {
        val directory = File(context.filesDir, NOTE_DIR).apply { mkdirs() }
        val target = existingPath?.let(::File)?.takeIf { it.parentFile == directory }
            ?: File(directory, name)
        if (target.name != name) {
            val renamed = File(directory, name)
            target.renameTo(renamed)
            renamed.writeText(body)
            return renamed
        }
        target.writeText(body)
        return target
    }

    fun read(path: String): String? = File(path).takeIf { it.isFile }?.readText()

    fun fileName(title: String): String {
        val safe = FileNameUtils.sanitize(title.trim().ifEmpty { UNTITLED })
        return if (safe.endsWith(EXTENSION, ignoreCase = true)) safe else "$safe$EXTENSION"
    }

    private companion object {
        const val NOTE_DIR = "notes"
        const val EXTENSION = ".md"
        const val UNTITLED = "Note"
    }
}
