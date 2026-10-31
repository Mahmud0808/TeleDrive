package com.drdisagree.teledrive.core.files

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

@RequiresApi(Build.VERSION_CODES.Q)
class MediaStorePartialDownload private constructor(
    private val resolver: ContentResolver,
    private val uri: Uri
) : PartialDownload {

    override val token: String get() = uri.toString()

    override fun length(): Long =
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L

    override fun truncate(size: Long) {
        resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).channel.truncate(size)
        } ?: error("No file to truncate")
    }

    override fun append(body: (OutputStream) -> Unit) {
        resolver.openOutputStream(uri, "wa")?.use(body) ?: error("No output stream")
    }

    override fun read(): InputStream =
        resolver.openInputStream(uri) ?: error("No input stream")

    override fun finish(): String? {
        val published = resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null
        ) > 0
        if (!published) return null
        return resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.toString()
    }

    override fun discard() {
        runCatching { resolver.delete(uri, null, null) }
    }

    companion object {

        fun open(
            resolver: ContentResolver,
            fileName: String,
            mimeType: String,
            relativePath: String,
            token: String?
        ): MediaStorePartialDownload? {
            token?.takeIf { it.startsWith(ContentResolver.SCHEME_CONTENT) }?.let(Uri::parse)
                ?.takeIf { exists(resolver, it) }
                ?.let { return MediaStorePartialDownload(resolver, it) }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            return MediaStorePartialDownload(resolver, uri)
        }

        private fun exists(resolver: ContentResolver, uri: Uri): Boolean = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
