package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.media.ThumbnailStore
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.data.local.dao.FilePartDao
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.FilePartEntity
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.core.files.MimeTypes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Uploads a file Telegram will not take whole, one part at a time.
 *
 * Each part is written to scratch space, sent, then deleted before the next is
 * built, so an encrypted upload never needs room for a second copy of the whole
 * file. Parts already recorded are skipped, which is what makes a paused or
 * interrupted upload continue from the part it stopped on rather than the start.
 */
class PartUploader(
    private val storagePaths: AppStoragePaths,
    private val telegramClient: TelegramClient,
    private val filePartDao: FilePartDao,
    private val manifestCodec: ManifestCodec,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val thumbnailStore: ThumbnailStore,
    private val dispatchers: DispatcherProvider
) {

    sealed interface Event {
        data class Progress(val transferredBytes: Long) : Event
        data class PartDone(val partIndex: Int, val partCount: Int) : Event
        data class Sealing(val partIndex: Int) : Event
        data class Completed(val parts: List<FilePartEntity>) : Event
    }

    fun upload(
        entity: FileEntity,
        source: File,
        chatId: Long,
        manifest: RemoteFileManifest,
        encrypt: Boolean
    ): Flow<Event> = flow {
        val totalSize = source.length()
        val partCount = FileParts.countFor(totalSize)
        val done = filePartDao.partsOf(entity.id)
            .filter { it.messageId != null }
            .associateBy { it.partIndex }
            .toMutableMap()

        var uploadedBefore = done.values.sumOf { it.plainSize }
        emit(Event.Progress(uploadedBefore))

        for (index in 0 until partCount) {
            if (done.containsKey(index)) continue

            val plainOffset = FileParts.offsetOf(index)
            val plainSize = FileParts.sizeOf(index, totalSize)
            val scratch = File(scratchDir(), "${entity.id}.${index}.part")

            try {
                if (encrypt) emit(Event.Sealing(index))
                withContext(dispatchers.io) {
                    writePart(source, plainOffset, plainSize, scratch, encrypt)
                }
                if (encrypt) emit(Event.PartDone(index, partCount))

                val rawIconPath = if (index == 0) {
                    thumbnailStore.uploadThumbnailFile(entity.id)?.absolutePath
                } else null

                var iconFileId: String? = null
                if (index == 0 && MimeTypes.isApk(entity.mimeType, entity.name) && rawIconPath != null) {
                    val iconUploadPath: String
                    val iconFileName: String
                    val iconMimeType: String
                    var stagingIconFile: File? = null

                    if (encrypt) {
                        val rawIconBytes = runCatching { File(rawIconPath).readBytes() }.getOrNull()
                        val key = runCatching { wrappedKeyRepository.getOrCreate(CryptoKeys.THUMBNAIL) }.getOrNull()
                        if (rawIconBytes != null && key != null) {
                            val encryptedBytes = streamCrypto.encryptBytes(key, rawIconBytes)
                            val staging = File(scratchDir(), "icon-${entity.id}.tde")
                            staging.writeBytes(encryptedBytes)
                            stagingIconFile = staging
                            iconUploadPath = staging.absolutePath
                            iconFileName = "icon-${entity.id}.tde"
                            iconMimeType = "application/octet-stream"
                        } else {
                            iconUploadPath = rawIconPath
                            iconFileName = "icon-${entity.id}.jpg"
                            iconMimeType = "image/jpeg"
                        }
                    } else {
                        iconUploadPath = rawIconPath
                        iconFileName = "icon-${entity.id}.jpg"
                        iconMimeType = "image/jpeg"
                    }

                    runCatching {
                        telegramClient.uploadDocument(
                            chatId = chatId,
                            localPath = iconUploadPath,
                            fileName = iconFileName,
                            mimeType = iconMimeType,
                            caption = "#teledrive-icon-${entity.id}",
                            thumbnailPath = null
                        ).first { it is TelegramUploadEvent.Completed } as? TelegramUploadEvent.Completed
                    }.getOrNull()?.let { completed ->
                        iconFileId = completed.document.remoteFileId
                    }
                    stagingIconFile?.delete()
                }

                val previewPath = if (index == 0 && !encrypt) rawIconPath else null

                val partManifest = manifest.copy(
                    version = RemoteFileManifest.PART_VERSION,
                    partCount = partCount,
                    partIndex = index,
                    partOffset = plainOffset,
                    partSize = plainSize,
                    iconFileId = if (index == 0) iconFileId ?: manifest.iconFileId else null
                )
                val partName = if (encrypt) {
                    FileParts.nameFor(entity.id, index)
                } else {
                    FileParts.nameFor(entity.name, index)
                }

                var stored: FilePartEntity? = null
                val alreadySent = uploadedBefore
                telegramClient.uploadDocument(
                    chatId = chatId,
                    localPath = scratch.absolutePath,
                    fileName = partName,
                    mimeType = if (encrypt) OCTET_STREAM else entity.mimeType,
                    caption = manifestCodec.encode(partManifest, encrypt),
                    thumbnailPath = previewPath
                ).collect { event ->
                    when (event) {
                        is TelegramUploadEvent.Started -> Unit
                        is TelegramUploadEvent.Progress -> {
                            val within = event.transferredBytes
                                .coerceAtMost(plainSize)
                            emit(Event.Progress(alreadySent + within))
                        }

                        is TelegramUploadEvent.Completed -> {
                            val document = event.document
                            stored = FilePartEntity(
                                fileId = entity.id,
                                partIndex = index,
                                chatId = document.chatId,
                                messageId = document.messageId,
                                remoteFileId = document.remoteFileId,
                                remoteUniqueId = document.uniqueFileId,
                                plainOffset = plainOffset,
                                plainSize = plainSize,
                                storedSize = scratch.length(),
                                uploadedAt = System.currentTimeMillis()
                            )
                        }
                    }
                }

                val part = stored ?: error("Part ${index + 1} did not finish")
                filePartDao.upsert(part)
                done[index] = part
                uploadedBefore += plainSize
                emit(Event.Progress(uploadedBefore))
                emit(Event.PartDone(index, partCount))
            } finally {
                withContext(NonCancellable + dispatchers.io) { scratch.delete() }
            }
        }

        emit(Event.Completed(done.values.sortedBy { it.partIndex }))
    }

    /** Removes whatever reached Telegram, for a canceled or deleted upload. */
    suspend fun discardParts(fileId: String) {
        val parts = filePartDao.partsOf(fileId)
        for ((chatId, group) in parts.groupBy { it.chatId }) {
            if (chatId == null) continue
            val messageIds = group.mapNotNull { it.messageId }
            if (messageIds.isEmpty()) continue
            runCatching { telegramClient.deleteMessages(chatId, messageIds) }
                .onFailure { SafeLog.w(TAG, "Could not drop ${messageIds.size} orphan parts", it) }
        }
        filePartDao.deleteFor(listOf(fileId))
    }

    private fun writePart(
        source: File,
        plainOffset: Long,
        plainSize: Long,
        target: File,
        encrypt: Boolean
    ) {
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            skipExactly(input, plainOffset)
            val ranged = RangeInputStream(input, plainSize)
            target.outputStream().buffered().use { output ->
                if (encrypt) {
                    val key = wrappedKeyRepository.getOrCreate(CryptoKeys.CONTENT)
                    streamCrypto.encryptStream(key, ranged, output)
                } else {
                    ranged.copyTo(output)
                }
            }
        }
    }

    private fun skipExactly(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) return
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun scratchDir(): File = File(storagePaths.cacheDir, SCRATCH_DIR).apply { mkdirs() }

    private companion object {
        const val TAG = "PartUploader"
        const val SCRATCH_DIR = "parts"
        const val OCTET_STREAM = "application/octet-stream"
    }
}

/** Reads at most [limit] bytes, so one part cannot run into the next. */
private class RangeInputStream(
    private val delegate: InputStream,
    private val limit: Long
) : InputStream() {

    private var read = 0L

    override fun read(): Int {
        if (read >= limit) return -1
        val value = delegate.read()
        if (value != -1) read++
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (read >= limit) return -1
        val allowed = minOf(length.toLong(), limit - read).toInt()
        val count = delegate.read(buffer, offset, allowed)
        if (count > 0) read += count
        return count
    }

    override fun available(): Int = minOf(delegate.available().toLong(), limit - read).toInt()
}
