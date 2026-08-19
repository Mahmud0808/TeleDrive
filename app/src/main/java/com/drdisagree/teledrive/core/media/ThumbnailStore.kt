package com.drdisagree.teledrive.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.media.ThumbnailUtils
import android.util.Size
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.ThumbnailDao
import com.drdisagree.teledrive.data.local.entity.ThumbnailEntity
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Generates and caches thumbnails. Cache files are AES-GCM sealed when
 * thumbnail encryption is enabled, so private media never sits readable in
 * the cache directory. Falls back to the Telegram mini-thumbnail when no
 * local copy exists.
 */
@Singleton
class ThumbnailStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileDao: FileDao,
    private val thumbnailDao: ThumbnailDao,
    private val telegramClient: TelegramClient,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val settingsRepository: SettingsRepository
) {

    private val generationSemaphore = Semaphore(3)

    suspend fun thumbnailBytes(fileId: String): ByteArray? {
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

    suspend fun uploadThumbnailFile(fileId: String): File? {
        val bytes = thumbnailBytes(fileId) ?: return null
        val dir = File(context.cacheDir, "upload-thumbs").apply { mkdirs() }
        val target = File(dir, "$fileId.jpg")
        return runCatching {
            target.writeBytes(bytes)
            target
        }.getOrNull()
    }

    private suspend fun generate(fileId: String): ByteArray? {
        val entity = fileDao.byId(fileId) ?: return null
        val bitmap = entity.localPath?.let { path ->
            val file = File(path)
            if (file.exists()) decodeThumbnail(file, entity.mimeType) else null
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

    private fun decodeThumbnail(file: File, mimeType: String): Bitmap? = runCatching {
        when {
            MimeTypes.isImage(mimeType) -> decodeImageThumbnail(file)
            MimeTypes.isVideo(mimeType) -> decodeVideoThumbnail(file)
            else -> null
        }
    }.getOrNull()

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
        private const val MAX_DIMENSION = 512
        private const val JPEG_QUALITY = 82
    }
}
