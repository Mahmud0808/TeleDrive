package com.drdisagree.teledrive.core.files

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.drdisagree.teledrive.core.common.SafeLog
import java.io.File

/**
 * Indexed media is deleted through MediaStore so the gallery entry goes too; on Android 11+ that
 * may need a confirmation, returned as an IntentSender.
 */
class MediaStoreLocalCopyDeleter(
    private val context: Context
) : LocalCopyDeleter {

    override fun delete(paths: List<String>): LocalCleanup {
        if (paths.isEmpty()) return LocalCleanup(0)

        val mediaUris = paths.mapNotNull { mediaUriFor(it) }
        if (needsUserConsent() && mediaUris.isNotEmpty()) {
            val request = runCatching {
                MediaStore.createDeleteRequest(context.contentResolver, mediaUris)
            }.getOrNull()
            if (request != null) return LocalCleanup(0, request.intentSender)
        }

        var deleted = 0
        for (path in paths) {
            if (deleteOne(path)) deleted++
        }
        return LocalCleanup(deleted)
    }

    override fun isGone(path: String): Boolean = !File(path).exists()

    private fun deleteOne(path: String): Boolean {
        val uri = mediaUriFor(path)
        if (uri != null) {
            val removed = runCatching {
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
            if (removed) return true
        }
        val file = File(path)
        if (!file.exists()) return file.parentFile?.canRead() == true
        return runCatching { file.delete() }.getOrDefault(false).also { success ->
            if (!success) SafeLog.d(TAG, "Local copy could not be removed")
        }
    }

    private fun mediaUriFor(path: String): Uri? = runCatching {
        val collection = MediaStore.Files.getContentUri(externalVolume())
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(path),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            } else {
                null
            }
        }
    }.getOrNull()

    private fun externalVolume(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.VOLUME_EXTERNAL
        } else {
            LEGACY_EXTERNAL_VOLUME
        }

    private fun needsUserConsent(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    private companion object {
        const val TAG = "LocalCopyDeleter"
        const val LEGACY_EXTERNAL_VOLUME = "external"
    }
}
