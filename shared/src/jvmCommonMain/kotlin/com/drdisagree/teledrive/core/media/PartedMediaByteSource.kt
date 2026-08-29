package com.drdisagree.teledrive.core.media

import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Reads a file split across several Telegram messages as though it were one.
 *
 * A position in the original file is mapped to the part holding it, so seeking
 * fetches only that part. Encrypted parts are sealed in fixed size frames, so a
 * seek decrypts the one frame it lands in rather than the whole part. As the
 * read approaches the end of a part the next one starts buffering, so playback
 * carries across the boundary without stalling.
 */
class PartedMediaByteSource(
    private val telegramClient: TelegramClient,
    private val streamCrypto: StreamCrypto,
    private val parts: List<MediaPart>,
    private val encrypted: Boolean,
    private val contentKey: ByteArray?
) : MediaByteSource {

    private class OpenPart(
        val part: MediaPart,
        val index: Int,
        val fileId: Int,
        val storedSize: Long,
        val salt: ByteArray?
    )

    private var open: OpenPart? = null
    private var preloaded: Int = -1
    private var frameIndex: Int = -1
    private var frame: ByteArray? = null

    private val totalSize: Long = parts.sumOf { it.plainSize }

    override suspend fun size(): Long = totalSize

    override suspend fun read(position: Long, count: Int): ByteArray {
        val part = parts.firstOrNull {
            position < it.plainOffset + it.plainSize && position >= it.plainOffset
        } ?: return ByteArray(0)
        val within = position - part.plainOffset
        val available = part.plainSize - within
        val toRead = minOf(count.toLong(), available).toInt()
        if (toRead <= 0) return ByteArray(0)

        val active = openPart(part)
        preloadNext(part, within)
        return if (encrypted) {
            readEncrypted(active, within, toRead)
        } else {
            readPlain(active, within, toRead)
        }
    }

    private suspend fun readPlain(active: OpenPart, within: Long, count: Int): ByteArray {
        readDownloaded(active.fileId, within, count.toLong())?.let { return it }
        awaitAvailable(active.fileId, within, count.toLong())
        return telegramClient.readFilePart(active.fileId, within, count.toLong())
    }

    private suspend fun readDownloaded(
        fileId: Int,
        position: Long,
        count: Long
    ): ByteArray? = runCatching {
        telegramClient.readFilePart(fileId, position, count).takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Frames are a fixed plaintext size, so the sealed bytes covering a position
     * are found by arithmetic instead of by reading from the start of the part.
     */
    private suspend fun readEncrypted(active: OpenPart, within: Long, count: Int): ByteArray {
        val key = contentKey ?: throw IOException("Encryption key missing")
        val salt = active.salt ?: throw IOException("Encrypted part has no header")
        val wanted = streamCrypto.frameIndexOf(within)

        if (wanted != frameIndex) {
            val start = streamCrypto.frameStart(wanted)
            if (start >= active.storedSize) return ByteArray(0)
            val span = minOf(
                streamCrypto.frameStoredSize(StreamCrypto.CHUNK_SIZE).toLong(),
                active.storedSize - start
            )
            val sealed = readDownloaded(active.fileId, start, span) ?: run {
                awaitAvailable(active.fileId, start, span)
                telegramClient.readFilePart(active.fileId, start, span)
            }
            frame = streamCrypto.decryptFrame(key, salt, wanted, sealed)
            frameIndex = wanted
        }

        val plain = frame ?: return ByteArray(0)
        val insideFrame = (within % StreamCrypto.CHUNK_SIZE).toInt()
        if (insideFrame >= plain.size) return ByteArray(0)
        val take = minOf(count, plain.size - insideFrame)
        return plain.copyOfRange(insideFrame, insideFrame + take)
    }

    private suspend fun openPart(part: MediaPart): OpenPart {
        val index = parts.indexOf(part)
        open?.takeIf { it.index == index }?.let { return it }

        val remoteFileId = part.remoteFileId
        val info = telegramClient.resolveFile(remoteFileId)
        val salt = if (encrypted) {
            val headerSize = streamCrypto.headerSize().toLong()
            awaitAvailable(info.fileId, 0, headerSize)
            streamCrypto.saltOf(telegramClient.readFilePart(info.fileId, 0, headerSize))
        } else {
            null
        }

        open?.let { previous ->
            runCatching { telegramClient.cancelFileDownload(previous.fileId) }
        }
        frameIndex = -1
        frame = null
        return OpenPart(part, index, info.fileId, info.sizeBytes, salt).also { open = it }
    }

    /** Starts the next part buffering before the current one runs out. */
    private suspend fun preloadNext(part: MediaPart, within: Long) {
        val nextIndex = parts.indexOf(part) + 1
        val next = parts.getOrNull(nextIndex) ?: return
        if (preloaded == nextIndex) return
        if (part.plainSize - within > PRELOAD_MARGIN) return

        val remoteFileId = next.remoteFileId
        runCatching {
            val info = telegramClient.resolveFile(remoteFileId)
            telegramClient.requestFileRange(info.fileId, 0, 0)
            preloaded = nextIndex
        }
    }

    private suspend fun awaitAvailable(fileId: Int, readPosition: Long, count: Long) {
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
        open?.let { active ->
            runCatching { runBlocking { telegramClient.cancelFileDownload(active.fileId) } }
        }
        open = null
        frame = null
        frameIndex = -1
        preloaded = -1
    }

    private companion object {
        const val BUFFER_TIMEOUT_MS = 30_000L
        const val PRELOAD_MARGIN = 8L * 1024 * 1024
    }
}
