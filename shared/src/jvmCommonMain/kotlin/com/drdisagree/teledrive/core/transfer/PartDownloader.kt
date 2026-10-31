package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.KeyUnavailableException
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.PartialDownload
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.data.local.dao.FilePartDao
import com.drdisagree.teledrive.data.local.entity.FilePartEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * The partly built file is kept between attempts, so a download resumes from the part it reached.
 */
class PartDownloader(
    private val telegramClient: TelegramClient,
    private val filePartDao: FilePartDao,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val dispatchers: DispatcherProvider
) {

    fun download(
        fileId: String,
        encrypted: Boolean,
        target: PartialDownload
    ): Flow<PartDownloadEvent> = flow {
        val parts = filePartDao.partsOf(fileId).sortedBy { it.partIndex }
        if (parts.isEmpty()) error("File has no parts on record")

        val resumeFrom = withContext(dispatchers.io) { resumePoint(target, parts) }
        var written = resumeFrom.second
        emit(PartDownloadEvent.Progress(written))

        for (part in parts.drop(resumeFrom.first)) {
            val remoteFileId = part.remoteFileId ?: error("Part ${part.partIndex + 1} is missing")
            var downloaded: String? = null
            telegramClient.downloadDocument(remoteFileId).collect { event ->
                when (event) {
                    is TelegramDownloadEvent.Progress ->
                        emit(PartDownloadEvent.Progress(written + event.transferredBytes.coerceAtMost(part.plainSize)))

                    is TelegramDownloadEvent.Completed -> downloaded = event.localPath
                }
            }

            val source = downloaded?.let(::File)?.takeIf { it.exists() }
                ?: error("Part ${part.partIndex + 1} did not download")

            emit(PartDownloadEvent.Joining(part.partIndex))
            withContext(dispatchers.io) {
                appendPart(source, target, encrypted)
                if (target.length() != written + part.plainSize) {
                    target.truncate(written)
                    error("Part ${part.partIndex + 1} was not written completely")
                }
            }
            runCatching { telegramClient.deleteLocalCopy(remoteFileId) }
                .onFailure { SafeLog.w(TAG, "Could not free part ${part.partIndex + 1}", it) }

            written += part.plainSize
            emit(PartDownloadEvent.Progress(written))
        }

        emit(PartDownloadEvent.Completed(target.token))
    }

    private fun appendPart(source: File, target: PartialDownload, encrypted: Boolean) {
        target.append { output ->
            val buffered = output.buffered()
            if (encrypted) {
                val key = wrappedKeyRepository.get(CryptoKeys.CONTENT)
                    ?: throw KeyUnavailableException(CryptoKeys.CONTENT)
                source.inputStream().buffered().use { input ->
                    streamCrypto.decryptStream(key, input, buffered)
                }
            } else {
                source.inputStream().buffered().use { input -> input.copyTo(buffered) }
            }
            buffered.flush()
        }
    }

    /** Only whole parts count: a half-written one cannot be trusted and is fetched again. */
    private fun resumePoint(target: PartialDownload, parts: List<FilePartEntity>): Pair<Int, Long> {
        val length = target.length()
        if (length == 0L) return 0 to 0L
        var covered = 0L
        var index = 0
        for (part in parts) {
            if (covered + part.plainSize > length) break
            covered += part.plainSize
            index++
        }
        if (covered != length) target.truncate(covered)
        return index to covered
    }

    private companion object {
        const val TAG = "PartDownloader"
    }
}
