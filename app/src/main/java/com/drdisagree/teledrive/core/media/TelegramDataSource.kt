package com.drdisagree.teledrive.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Media3 DataSource that streams a Telegram file through TDLib's ranged
 * download. Playback starts as soon as the requested byte range is buffered
 * instead of waiting for the whole file. Media3 calls these methods on its own
 * IO thread, so blocking on TDLib here is safe.
 */
@UnstableApi
class TelegramDataSource(
    private val telegramClient: TelegramClient,
    private val remoteFileId: String
) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var fileId: Int = 0
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        val info = runTelegram("resolve") {
            telegramClient.resolveFile(remoteFileId)
        }
        fileId = info.fileId
        position = dataSpec.position

        val totalSize = info.sizeBytes
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            totalSize - position
        }
        if (bytesRemaining < 0) throw IOException("Position beyond end of file")

        if (!info.isDownloadingCompleted) {
            runTelegram("request range") {
                telegramClient.requestFileRange(fileId, position, 0)
            }
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = minOf(length.toLong(), bytesRemaining)
        val data = runTelegram("read") {
            awaitAvailable(position, toRead)
            telegramClient.readFilePart(fileId, position, toRead)
        }
        if (data.isEmpty()) return C.RESULT_END_OF_INPUT

        System.arraycopy(data, 0, buffer, offset, data.size)
        position += data.size
        bytesRemaining -= data.size
        bytesTransferred(data.size)
        return data.size
    }

    private suspend fun awaitAvailable(readPosition: Long, count: Long) {
        val info = telegramClient.getFileInfo(fileId)
        if (covers(info, readPosition, count)) return

        if (readPosition < info.downloadOffset ||
            readPosition > info.downloadOffset + info.downloadedPrefixSize
        ) {
            telegramClient.requestFileRange(fileId, readPosition, 0)
        }
        withTimeout(BUFFER_TIMEOUT_MS) {
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
            runCatching {
                runBlocking { telegramClient.cancelFileDownload(fileId) }
            }
            transferEnded()
        }
        uri = null
    }

    private fun <T> runTelegram(stage: String, block: suspend () -> T): T = try {
        runBlocking { block() }
    } catch (e: Exception) {
        throw IOException("Telegram stream $stage failed", e)
    }

    companion object {
        private const val BUFFER_TIMEOUT_MS = 30_000L
    }
}
