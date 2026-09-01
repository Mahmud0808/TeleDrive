package com.drdisagree.teledrive.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.files.Urls
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.ThumbnailDao
import com.drdisagree.teledrive.data.local.entity.ThumbnailEntity
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Generates and caches thumbnails. Cache files are AES-GCM sealed when
 * thumbnail encryption is enabled, so private media never sits readable in
 * the cache directory. Falls back to the Telegram mini-thumbnail when no
 * local copy exists.
 */
class AndroidThumbnailStore(
    private val context: Context,
    private val fileDao: FileDao,
    private val thumbnailDao: ThumbnailDao,
    private val telegramClient: TelegramClient,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val settingsRepository: SettingsRepository
) : ThumbnailStore {

    private val generationSemaphore = Semaphore(3)

    override suspend fun thumbnailBytes(fileId: String): ByteArray? {
        if (!settingsRepository.preferences.first().linkPreviews && isNote(fileId)) {
            thumbnailDao.delete(fileId)
            return null
        }
        thumbnailDao.byFileId(fileId)?.let { cached ->
            val file = File(cached.path)
            if (file.exists()) {
                thumbnailDao.touch(fileId, System.currentTimeMillis())
                val raw = file.readBytes()
                return if (cached.encrypted) {
                    runCatching {
                        streamCrypto.decryptBytes(thumbnailKey(), raw)
                    }.getOrNull()
                } else raw
            }
            thumbnailDao.delete(fileId)
        }
        return generationSemaphore.withPermit { generate(fileId) }
    }

    override suspend fun uploadThumbnailFile(fileId: String): File? {
        val bytes = thumbnailBytes(fileId) ?: return null
        val dir = File(context.cacheDir, "upload-thumbs").apply { mkdirs() }
        val target = File(dir, "$fileId.jpg")
        return runCatching {
            target.writeBytes(bytes)
            target
        }.getOrNull()
    }

    private suspend fun isNote(fileId: String): Boolean =
        fileDao.byId(fileId)?.let { MimeTypes.isText(it.mimeType) } == true

    private suspend fun generate(fileId: String): ByteArray? {
        val entity = fileDao.byId(fileId) ?: return null
        val bitmap = entity.localPath?.let { path ->
            val file = File(path)
            when {
                !file.exists() -> null
                MimeTypes.isText(entity.mimeType) -> linkThumbnail(file)
                else -> decodeThumbnail(file, entity.mimeType)
            }
        }

        val jpegBytes = if (bitmap != null) {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                bitmap.recycle()
                output.toByteArray()
            }
        } else {
            val chatId = entity.chatId
            val messageId = entity.messageId
            if (chatId == null || messageId == null) return null
            // The link image travels with the upload, so the server still holds
            // one after previews are switched off.
            if (MimeTypes.isText(entity.mimeType) &&
                !settingsRepository.preferences.first().linkPreviews
            ) return null
            runCatching { telegramClient.fetchThumbnail(chatId, messageId) }.getOrNull()
                ?: return null
        }

        val encrypt = settingsRepository.preferences.first().encryptThumbnails
        val stored = if (encrypt) {
            streamCrypto.encryptBytes(thumbnailKey(), jpegBytes)
        } else jpegBytes

        val dir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
        val target = File(dir, "$fileId.thumb")
        runCatching { target.writeBytes(stored) }.onFailure { return jpegBytes }

        val now = System.currentTimeMillis()
        thumbnailDao.upsert(
            ThumbnailEntity(
                fileId = fileId,
                path = target.absolutePath,
                encrypted = encrypt,
                width = MAX_DIMENSION,
                height = MAX_DIMENSION,
                sizeBytes = stored.size.toLong(),
                generatedAt = now,
                lastAccessAt = now
            )
        )
        return jpegBytes
    }

    /** A note holding just a link previews as that link's image. */
    private suspend fun linkThumbnail(file: File): Bitmap? {
        if (file.length() > LINK_NOTE_LIMIT) return null
        if (!settingsRepository.preferences.first().linkPreviews) return null
        val url = Urls.sole(file.readText()) ?: return null
        val imagePath = telegramClient
            .linkPreview(Urls.normalize(url), withImage = true)
            ?.imagePath
            ?: return null
        return File(imagePath).takeIf { it.exists() }?.let(::decodeImageThumbnail)
    }

    private fun decodeThumbnail(file: File, mimeType: String): Bitmap? = runCatching {
        when {
            MimeTypes.isImage(mimeType) -> decodeImageThumbnail(file)
            MimeTypes.isVideo(mimeType) -> decodeVideoThumbnail(file)
            MimeTypes.isApk(mimeType) -> decodeApkIcon(file)
            else -> null
        }
    }.getOrNull()

    private fun decodeApkIcon(file: File): Bitmap? {
        val bitmapFromPm = runCatching {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0)
            val appInfo = info?.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
                val drawable = appInfo.loadIcon(pm)
                if (drawable != null) {
                    return@runCatching drawableToBitmap(drawable)
                }
            }
            null
        }.getOrNull()

        if (bitmapFromPm != null) return bitmapFromPm

        val bytes = ApkIconExtractor.extractIconBytes(file) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else MAX_DIMENSION
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else MAX_DIMENSION
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun decodeImageThumbnail(file: File): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThumbnailUtils.createImageThumbnail(file, Size(MAX_DIMENSION, MAX_DIMENSION), null)
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val sample = maxOf(
                1,
                maxOf(options.outWidth, options.outHeight) / MAX_DIMENSION
            )
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }

    private fun decodeVideoThumbnail(file: File): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThumbnailUtils.createVideoThumbnail(file, Size(MAX_DIMENSION, MAX_DIMENSION), null)
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.frameAtTime
            } finally {
                retriever.release()
            }
        }

    private fun thumbnailKey(): ByteArray =
        wrappedKeyRepository.getOrCreate(CryptoKeys.THUMBNAIL)

    companion object {
        private const val LINK_NOTE_LIMIT = 8L * 1024
        private const val MAX_DIMENSION = 512
        private const val JPEG_QUALITY = 82
    }
}
