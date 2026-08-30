package com.drdisagree.teledrive.core.media

import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Reads a single Telegram file through TDLib's ranged download. Bytes become
 * readable as soon as the requested range is buffered instead of after the
 * whole file lands.
 */
class TelegramMediaByteSource(
    private val telegramClient: TelegramClient,
    private val remoteFileId: String
) : MediaByteSource {

    private var fileId: Int = 0
    private var totalSize: Long = -1

    override suspend fun size(): Long {
        resolve()
        return totalSize
    }

    override suspend fun read(position: Long, count: Int): ByteArray {
        resolve()
        val toRead = minOf(count.toLong(), totalSize - position)
        if (toRead <= 0) return ByteArray(0)
        readDownloaded(fileId, position, toRead)?.let { return it }
        awaitAvailable(position, toRead)
        return telegramClient.readFilePart(fileId, position, toRead)
    }

    private suspend fun readDownloaded(
        fileId: Int,
        position: Long,
        count: Long
    ): ByteArray? = runCatching {
        telegramClient.readFilePart(fileId, position, count).takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend fun resolve() {
        if (totalSize >= 0) return
        val info = telegramClient.resolveFile(remoteFileId)
        fileId = info.fileId
        totalSize = info.sizeBytes
        if (!info.isDownloadingCompleted) {
            telegramClient.requestFileRange(fileId, 0, 0)
        }
    }

    private suspend fun awaitAvailable(readPosition: Long, count: Long) {
        val info = telegramClient.getFileInfo(fileId)
        if (covers(info, readPosition, count)) return

        if (readPosition < info.downloadOffset ||
            readPosition > info.downloadOffset + info.downloadedPrefixSize
        ) {
            telegramClient.requestFileRange(fileId, readPosition, 0)
        }
        withTimeout(BUFFER_TIMEOUT_MS.milliseconds) {
            telegramClient.fileUpdates(fileId).first { covers(it, readPosition, count) }
        }
    }

    private fun covers(info: TelegramFileInfo, readPosition: Long, count: Long): Boolean {
        if (info.isDownloadingCompleted) return true
        val start = info.downloadOffset
        val end = start + info.downloadedPrefixSize
        return readPosition >= start && readPosition + count <= end
    }

    override fun close() {
        if (fileId != 0) {
            runCatching { runBlocking { telegramClient.cancelFileDownload(fileId) } }
        }
    }

    private companion object {
        const val BUFFER_TIMEOUT_MS = 30_000L
    }
}
