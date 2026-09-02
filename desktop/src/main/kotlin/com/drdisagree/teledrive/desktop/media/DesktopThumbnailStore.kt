package com.drdisagree.teledrive.desktop.media

import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.files.Urls
import com.drdisagree.teledrive.core.media.ThumbnailStore
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.ThumbnailDao
import com.drdisagree.teledrive.data.local.entity.ThumbnailEntity
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import com.drdisagree.teledrive.core.media.ApkIconExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Generates and caches thumbnails. Cache files are AES-GCM sealed when
 * thumbnail encryption is enabled, so private media never sits readable in
 * the cache directory. Images are scaled locally; everything else falls back
 * to the thumbnail Telegram already holds for the message.
 */
class DesktopThumbnailStore(
    private val storagePaths: AppStoragePaths,
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
        val dir = File(storagePaths.cacheDir, "upload-thumbs").apply { mkdirs() }
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
        val local = entity.localPath?.let { path ->
            val file = File(path)
            when {
                !file.exists() -> null
                MimeTypes.isText(entity.mimeType) -> linkThumbnail(file)
                MimeTypes.isImage(entity.mimeType) -> decodeImageThumbnail(file)
                MimeTypes.isApk(entity.mimeType, file.name) -> decodeApkIcon(file)
                else -> null
            }
        }

        val jpegBytes = if (local != null) {
            toJpeg(local)
        } else {
            val iconFileId = entity.iconFileId
            if (iconFileId != null) {
                fetchIconBytes(entity, iconFileId) ?: fetchRemoteThumbnail(entity)
            } else {
                fetchRemoteThumbnail(entity)
            }
        } ?: return null

        val encrypt = settingsRepository.preferences.first().encryptThumbnails
        val stored = if (encrypt) {
            streamCrypto.encryptBytes(thumbnailKey(), jpegBytes)
        } else jpegBytes

        val dir = File(storagePaths.cacheDir, "thumbnails").apply { mkdirs() }
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
    private suspend fun linkThumbnail(file: File): BufferedImage? {
        if (file.length() > LINK_NOTE_LIMIT) return null
        if (!settingsRepository.preferences.first().linkPreviews) return null
        val url = Urls.sole(file.readText()) ?: return null
        val imagePath = telegramClient
            .linkPreview(Urls.normalize(url), withImage = true)
            ?.imagePath
            ?: return null
        return File(imagePath).takeIf { it.exists() }?.let(::decodeImageThumbnail)
    }

    private fun decodeApkIcon(file: File): BufferedImage? {
        val bytes = ApkIconExtractor.extractIconBytes(file) ?: return null
        return runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
    }

    private fun decodeImageThumbnail(file: File): BufferedImage? = runCatching {
        val original = ImageIO.read(file) ?: return null
        if (original.width <= MAX_DIMENSION && original.height <= MAX_DIMENSION) {
            return@runCatching original
        }
        val scale = minOf(
            MAX_DIMENSION.toDouble() / original.width,
            MAX_DIMENSION.toDouble() / original.height
        )
        val targetW = (original.width * scale).toInt().coerceAtLeast(1)
        val targetH = (original.height * scale).toInt().coerceAtLeast(1)
        val scaled = original.getScaledInstance(targetW, targetH, IMAGE_SCALE_MODE)
        BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB).also { copy ->
            val graphics = copy.createGraphics()
            graphics.drawImage(scaled, 0, 0, Color.WHITE, null)
            graphics.dispose()
        }
    }.getOrNull()

    private fun toJpeg(image: BufferedImage): ByteArray {
        val rgb = if (image.type == BufferedImage.TYPE_INT_RGB) {
            image
        } else {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { copy ->
                val graphics = copy.createGraphics()
                graphics.drawImage(image, 0, 0, Color.WHITE, null)
                graphics.dispose()
            }
        }
        return ByteArrayOutputStream().use { output ->
            ImageIO.write(rgb, "jpg", output)
            output.toByteArray()
        }
    }

    private suspend fun fetchIconBytes(entity: FileEntity, iconFileId: String): ByteArray? {
        var readyPath: String? = null
        runCatching {
            telegramClient.downloadDocument(iconFileId).collect { event ->
                if (event is TelegramDownloadEvent.Completed) {
                    readyPath = event.localPath
                }
            }
        }
        val path = readyPath ?: return null
        val rawBytes = runCatching { File(path).readBytes() }.getOrNull() ?: return null
        return if (entity.isEncrypted) {
            val key = runCatching { thumbnailKey() }.getOrNull() ?: return null
            runCatching { streamCrypto.decryptBytes(key, rawBytes) }.getOrNull()
        } else {
            rawBytes
        }
    }

    private suspend fun fetchRemoteThumbnail(entity: FileEntity): ByteArray? {
        val chatId = entity.chatId ?: return null
        val messageId = entity.messageId ?: return null
        if (MimeTypes.isText(entity.mimeType) &&
            !settingsRepository.preferences.first().linkPreviews
        ) return null
        return runCatching { telegramClient.fetchThumbnail(chatId, messageId) }.getOrNull()
    }

    private fun thumbnailKey(): ByteArray =
        wrappedKeyRepository.getOrCreate(CryptoKeys.THUMBNAIL)

    private companion object {
        const val LINK_NOTE_LIMIT = 8L * 1024
        const val MAX_DIMENSION = 512
        const val IMAGE_SCALE_MODE = Image.SCALE_SMOOTH
    }
}
