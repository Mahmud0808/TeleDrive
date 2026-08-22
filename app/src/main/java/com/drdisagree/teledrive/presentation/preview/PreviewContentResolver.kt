package com.drdisagree.teledrive.presentation.preview

import android.content.Context
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.data.local.dao.CacheDao
import com.drdisagree.teledrive.data.local.entity.CacheEntryEntity
import com.drdisagree.teledrive.data.local.entity.CacheEntryType
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.lingala.zip4j.ZipFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a [DriveFile] into displayable preview content, fetching remote files
 * into the preview cache when they are small enough. Original local files are
 * always plaintext; only remote copies and caches are ever encrypted.
 */
@Singleton
class PreviewContentResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramClient: TelegramClient,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val cacheDao: CacheDao,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider
) {

    fun resolve(file: DriveFile): Flow<PreviewContent> = flow {
        emit(PreviewContent.Loading)
        val localPath = file.localPath?.takeIf { File(it).exists() }
        if (localPath != null) {
            emit(fromLocal(file, localPath))
            return@flow
        }

        if (!file.hasRemoteCopy || file.remoteFileId == null) {
            emit(PreviewContent.Failed(R.string.preview_no_copy_available))
            return@flow
        }

        val prefs = settingsRepository.preferences.first()
        if (prefs.streamBeforeDownload &&
            !file.isEncrypted &&
            (file.category == FileCategory.VIDEO || file.category == FileCategory.AUDIO)
        ) {
            emit(
                PreviewContent.StreamedMedia(
                    remoteFileId = file.remoteFileId,
                    isAudio = file.category == FileCategory.AUDIO
                )
            )
            return@flow
        }

        /* Only small files a viewer cannot show any other way are fetched on
           open, so browsing stays instant while a large transfer never starts
           without the user asking. */
        val autoFetchable = file.sizeBytes <= AUTO_PREVIEW_LIMIT &&
                (MimeTypes.isImage(file.mimeType) || MimeTypes.isText(file.mimeType))
        if (!autoFetchable) {
            emit(PreviewContent.RequiresDownload(file.sizeBytes))
            return@flow
        }

        val cached = fetchToCache(file) { transferred, total ->
            emit(PreviewContent.DownloadProgress(transferred, total))
        }
        if (cached == null) {
            emit(PreviewContent.Failed(R.string.preview_fetch_failed))
        } else {
            emit(fromLocal(file, cached.absolutePath))
        }
    }.flowOn(dispatchers.io)

    private fun fromLocal(file: DriveFile, path: String): PreviewContent = when {
        MimeTypes.isImage(file.mimeType) -> PreviewContent.Image(path)
        MimeTypes.isVideo(file.mimeType) -> PreviewContent.LocalMedia(path, isAudio = false)
        MimeTypes.isAudio(file.mimeType) -> PreviewContent.LocalMedia(path, isAudio = true)
        MimeTypes.isPdf(file.mimeType) -> PreviewContent.Pdf(path)
        MimeTypes.isText(file.mimeType) -> readText(path)
        MimeTypes.isArchive(file.mimeType) -> readArchive(file, path)
        else -> PreviewContent.Unsupported(R.string.preview_no_preview_for_type)
    }

    private fun readText(path: String): PreviewContent = runCatching {
        val target = File(path)
        val truncated = target.length() > TEXT_LIMIT
        val bytes = target.inputStream().use { stream ->
            val buffer = ByteArray(TEXT_LIMIT)
            var read = 0
            while (read < TEXT_LIMIT) {
                val count = stream.read(buffer, read, TEXT_LIMIT - read)
                if (count <= 0) break
                read += count
            }
            buffer.copyOf(read)
        }
        PreviewContent.PlainText(String(bytes, Charsets.UTF_8), truncated)
    }.getOrElse { PreviewContent.Failed(R.string.preview_read_failed) }

    private fun readArchive(file: DriveFile, path: String): PreviewContent {
        if (file.mimeType != "application/zip") {
            return PreviewContent.Unsupported(R.string.preview_only_zip)
        }
        return runCatching {
            val entries = ZipFile(path).fileHeaders.map { header ->
                PreviewContent.ArchiveEntry(
                    name = header.fileName,
                    sizeBytes = header.uncompressedSize,
                    compressedBytes = header.compressedSize,
                    isDirectory = header.isDirectory
                )
            }
            PreviewContent.Archive(entries, "ZIP")
        }.getOrElse { PreviewContent.Failed(R.string.preview_archive_failed) }
    }

    private suspend fun fetchToCache(
        file: DriveFile,
        onProgress: suspend (Long, Long) -> Unit
    ): File? {
        val previewDir = File(context.cacheDir, "previews").apply { mkdirs() }
        val target = File(previewDir, "${file.id}-${file.name}")
        if (target.exists() && target.length() > 0) {
            cacheDao.touch(target.absolutePath, System.currentTimeMillis())
            return target
        }

        var tdlibPath: String? = null
        telegramClient.downloadDocument(file.remoteFileId ?: return null)
            .collect { event ->
                when (event) {
                    is TelegramDownloadEvent.Progress ->
                        onProgress(event.transferredBytes, event.totalBytes)

                    is TelegramDownloadEvent.Completed -> tdlibPath = event.localPath
                }
            }
        val source = tdlibPath?.let(::File)?.takeIf { it.exists() } ?: return null

        val success = runCatching {
            if (file.isEncrypted) {
                val key = wrappedKeyRepository.get(CryptoKeys.CONTENT)
                    ?: error("Encryption key missing")
                source.inputStream().buffered().use { input ->
                    target.outputStream().buffered().use { output ->
                        streamCrypto.decryptStream(key, input, output)
                    }
                }
            } else {
                source.copyTo(target, overwrite = true)
            }
        }.isSuccess

        if (!success) {
            target.delete()
            return null
        }
        val now = System.currentTimeMillis()
        cacheDao.upsert(
            CacheEntryEntity(
                path = target.absolutePath,
                fileId = file.id,
                type = CacheEntryType.PREVIEW,
                sizeBytes = target.length(),
                encrypted = false,
                createdAt = now,
                lastAccessAt = now
            )
        )
        return target
    }

    companion object {
        private const val AUTO_PREVIEW_LIMIT = 10L * 1024 * 1024
        private const val TEXT_LIMIT = 256 * 1024
    }
}
