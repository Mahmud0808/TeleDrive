package com.drdisagree.teledrive.core.files

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * Maps SAF URIs to filesystem paths. Removable volumes are resolved through
 * [StorageManager] because their mount point is a per-device UUID, not a fixed
 * path. Providers that own no real file, such as cloud storage, resolve to null.
 */
object DocumentTreePaths {

    fun treeToFilePath(context: Context, treeUri: Uri): String? {
        val documentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return null

        val parts = documentId.split(":", limit = 2)
        val volumeId = parts[0]
        val relativePath = parts.getOrNull(1).orEmpty()

        val root = when (volumeId) {
            PRIMARY_VOLUME -> Environment.getExternalStorageDirectory().absolutePath
            else -> removableVolumeRoot(context, volumeId)
        } ?: return null

        val path = if (relativePath.isEmpty()) root else "$root/$relativePath"
        return path.takeIf { File(it).isDirectory }
    }

    /** Maps a single picked document to its file path, or null when it has none. */
    fun documentToFilePath(context: Context, uri: Uri): String? =
        mediaStorePath(context, uri) ?: externalDocumentPath(context, uri)

    private fun mediaStorePath(context: Context, uri: Uri): String? = runCatching {
        val mediaUri = when {
            uri.authority == MediaStore.AUTHORITY -> uri
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStore.getMediaUri(context, uri)
            else -> null
        } ?: return@runCatching null
        context.contentResolver.query(
            mediaUri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { File(it).isFile }
    }.getOrNull()

    private fun externalDocumentPath(context: Context, uri: Uri): String? = runCatching {
        if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return@runCatching null
        if (!DocumentsContract.isDocumentUri(context, uri)) return@runCatching null
        val parts = DocumentsContract.getDocumentId(uri).split(":", limit = 2)
        val relativePath = parts.getOrNull(1).orEmpty()
        if (relativePath.isEmpty()) return@runCatching null
        val root = when (val volumeId = parts[0]) {
            PRIMARY_VOLUME -> Environment.getExternalStorageDirectory().absolutePath
            else -> removableVolumeRoot(context, volumeId)
        } ?: return@runCatching null
        "$root/$relativePath".takeIf { File(it).isFile }
    }.getOrNull()

    private fun removableVolumeRoot(context: Context, volumeId: String): String? {
        val storageManager = context.getSystemService(StorageManager::class.java)
            ?: return null
        storageManager.storageVolumes.forEach { volume ->
            val uuid = volume.uuid ?: return@forEach
            if (uuid.equals(volumeId, ignoreCase = true)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory?.absolutePath?.let { return it }
                }
                return "$REMOVABLE_MOUNT_ROOT/$uuid"
            }
        }
        return "$REMOVABLE_MOUNT_ROOT/$volumeId".takeIf { File(it).isDirectory }
    }

    private const val PRIMARY_VOLUME = "primary"
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val REMOVABLE_MOUNT_ROOT = "/storage"
}
