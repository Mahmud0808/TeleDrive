package com.drdisagree.teledrive.core.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.drdisagree.teledrive.core.common.SafeLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a SAF-picked document into a real path the transfer engine can upload
 * and resume from. When the picked document is a file this app can already read
 * directly, that original path is used so the upload does not duplicate the
 * bytes and "Free up space" can reclaim the real copy later. Anything else
 * (cloud providers, in-memory providers) is copied into app storage, because a
 * content URI permission is not guaranteed to survive a reboot.
 */
@Singleton
class FileImporter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** [name] is what the drive shows; [path] may carry a staging suffix. */
    data class Imported(val path: String, val name: String, val sizeBytes: Long)

    fun import(uri: Uri): Imported? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val direct = uri.path?.let(::File)?.takeIf { it.isFile }
            if (direct != null) {
                return Imported(direct.absolutePath, direct.name, direct.length())
            }
        }
        readablePath(uri)?.let { source ->
            return Imported(source.absolutePath, source.name, source.length())
        }

        val metadata = queryMetadata(uri) ?: return null
        val displayName = FileNameUtils.sanitize(metadata.first)
        val targetDir = File(context.filesDir, IMPORT_DIR).apply { mkdirs() }

        val staged = File(targetDir, displayName)
        if (staged.isFile && staged.length() == metadata.second && metadata.second > 0) {
            return Imported(staged.absolutePath, displayName, staged.length())
        }

        val existingNames = targetDir.list()?.toSet().orEmpty()
        val stagingName = FileNameUtils.uniqueName(displayName) { it in existingNames }
        val target = File(targetDir, stagingName)

        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: return null
            Imported(target.absolutePath, displayName, target.length())
        }.getOrElse {
            SafeLog.w(TAG, "Import failed", it)
            target.delete()
            null
        }
    }

    /** Drops a staged copy the drive turned out not to need. */
    fun discard(imported: Imported) {
        val staged = File(imported.path)
        if (staged.parentFile?.name == IMPORT_DIR) staged.delete()
    }

    private fun readablePath(uri: Uri): File? {
        val candidate = DocumentTreePaths.documentToFilePath(context, uri) ?: return null
        val file = File(candidate)
        return file.takeIf { it.isFile && it.canRead() && it.length() > 0 }
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long>? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            (name ?: uri.lastPathSegment ?: "file") to size
        }

    companion object {
        private const val TAG = "FileImporter"
        private const val IMPORT_DIR = "imports"
    }
}
