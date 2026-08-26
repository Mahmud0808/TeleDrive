package com.drdisagree.teledrive.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Streams a file split across several Telegram messages as though it were one.
 *
 * A position in the original file is mapped to the part holding it, so seeking
 * fetches only that part. Encrypted parts are sealed in fixed size frames, so a
 * seek decrypts the one frame it lands in rather than the whole part. As the
 * read approaches the end of a part the next one starts buffering, so playback
 * carries across the boundary without stalling.
 */
@UnstableApi
class PartedTelegramDataSource(
    private val telegramClient: TelegramClient,
    private val streamCrypto: StreamCrypto,
    private val parts: List<MediaPart>,
    private val encrypted: Boolean,
    private val contentKey: ByteArray?
) : BaseDataSource(true) {

    private class OpenPart(
        val part: MediaPart,
        val index: Int,
        val fileId: Int,
        val storedSize: Long,
        val salt: ByteArray?
    )

    private var uri: Uri? = null
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    private var open: OpenPart? = null
    private var preloaded: Int = -1
    private var frameIndex: Int = -1
    private var frame: ByteArray? = null

    private val totalSize: Long get() = parts.sumOf { it.plainSize }

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        position = dataSpec.position

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            totalSize - position
        }
        if (bytesRemaining < 0) throw IOException("Position beyond end of file")

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val part = parts.firstOrNull { position < it.plainOffset + it.plainSize && position >= it.plainOffset }
            ?: return C.RESULT_END_OF_INPUT
        val within = position - part.plainOffset
        val available = part.plainSize - within
        val toRead = minOf(length.toLong(), bytesRemaining, available).toInt()
        if (toRead <= 0) return C.RESULT_END_OF_INPUT

        val data = runTelegram("read") {
            val active = openPart(part)
            preloadNext(part, within)
            if (encrypted) readEncrypted(active, within, toRead) else readPlain(active, within, toRead)
        }
        if (data.isEmpty()) return C.RESULT_END_OF_INPUT

        System.arraycopy(data, 0, buffer, offset, data.size)
        position += data.size
        bytesRemaining -= data.size
        bytesTransferred(data.size)
        return data.size
    }

    private suspend fun readPlain(active: OpenPart, within: Long, count: Int): ByteArray {
        awaitAvailable(active.fileId, within, count.toLong())
        return telegramClient.readFilePart(active.fileId, within, count.toLong())
    }

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
            awaitAvailable(active.fileId, start, span)
            val sealed = telegramClient.readFilePart(active.fileId, start, span)
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

        val remoteFileId = part.remoteFileId ?: throw IOException("Part has no remote copy")
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

        val remoteFileId = next.remoteFileId ?: return
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

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            open?.let { active ->
                runCatching { runBlocking { telegramClient.cancelFileDownload(active.fileId) } }
            }
            transferEnded()
        }
        open = null
        frame = null
        frameIndex = -1
        preloaded = -1
        uri = null
    }

    private fun <T> runTelegram(stage: String, block: suspend () -> T): T = try {
        runBlocking { block() }
    } catch (e: Exception) {
        throw IOException("Telegram stream $stage failed", e)
    }

    private companion object {
        const val BUFFER_TIMEOUT_MS = 30_000L
        const val PRELOAD_MARGIN = 8L * 1024 * 1024
    }
}
