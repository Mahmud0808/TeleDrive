package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.media.hasLocalCopy
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import java.io.File
import java.io.RandomAccessFile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class UploadVerifier(
    private val telegramClient: TelegramClient,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val fetchTimeout: Duration = 90.seconds
) {

    suspend fun verify(source: File, pieces: List<UploadedPiece>, encrypted: Boolean): UploadCheck {
        var unverified = false
        for (piece in pieces) {
            val check = try {
                checkPiece(source, piece, encrypted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SafeLog.w(TAG, "Could not verify an uploaded piece", e)
                UploadCheck.UNVERIFIED
            }
            when (check) {
                UploadCheck.DAMAGED -> return UploadCheck.DAMAGED
                UploadCheck.UNVERIFIED -> unverified = true
                UploadCheck.VERIFIED -> Unit
            }
        }
        return if (unverified) UploadCheck.UNVERIFIED else UploadCheck.VERIFIED
    }

    private suspend fun checkPiece(
        source: File,
        piece: UploadedPiece,
        encrypted: Boolean
    ): UploadCheck {
        val document = telegramClient.getDocument(piece.chatId, piece.messageId)
            ?: return UploadCheck.UNVERIFIED
        if (document.sizeBytes != piece.storedSize) {
            SafeLog.w(TAG, "Uploaded ${document.sizeBytes} bytes, expected ${piece.storedSize}")
            return UploadCheck.DAMAGED
        }

        val sampleSize = minOf(StreamCrypto.CHUNK_SIZE.toLong(), piece.plainSize).toInt()
        val fetchSize = if (encrypted) {
            streamCrypto.frameStart(0) + streamCrypto.frameStoredSize(sampleSize)
        } else {
            sampleSize.toLong()
        }
        val remote = fetchPrefix(document.remoteFileId, fetchSize)
            ?: return UploadCheck.UNVERIFIED
        val plain = if (encrypted) {
            val key = wrappedKeyRepository.get(CryptoKeys.CONTENT) ?: return UploadCheck.UNVERIFIED
            decryptFirstFrame(key, remote) ?: return UploadCheck.DAMAGED
        } else {
            remote
        }
        return if (plain.contentEquals(readLocal(source, piece.plainOffset, sampleSize))) {
            UploadCheck.VERIFIED
        } else {
            SafeLog.w(TAG, "Uploaded bytes do not match the local file")
            UploadCheck.DAMAGED
        }
    }

    private fun decryptFirstFrame(key: ByteArray, sealed: ByteArray): ByteArray? = runCatching {
        val headerSize = streamCrypto.headerSize()
        val salt = streamCrypto.saltOf(sealed.copyOfRange(0, headerSize))
        val frameStart = streamCrypto.frameStart(0).toInt()
        streamCrypto.decryptFrame(key, salt, 0, sealed.copyOfRange(frameStart, sealed.size))
    }.onFailure { SafeLog.w(TAG, "Uploaded file does not decrypt", it) }.getOrNull()

    private suspend fun fetchPrefix(remoteFileId: String, length: Long): ByteArray? {
        val info = telegramClient.resolveFile(remoteFileId)
        return try {
            telegramClient.requestFileRange(info.fileId, 0, length)
            withTimeoutOrNull(fetchTimeout) {
                while (!holds(telegramClient.getFileInfo(info.fileId), length)) {
                    withTimeoutOrNull(RECHECK_INTERVAL_MS.milliseconds) {
                        telegramClient.fileUpdates(info.fileId).first { holds(it, length) }
                    }
                }
            } ?: return null
            telegramClient.readFilePart(info.fileId, 0, length)
        } finally {
            runCatching { telegramClient.cancelFileDownload(info.fileId) }
            runCatching { telegramClient.deleteLocalCopy(remoteFileId) }
        }
    }

    private fun holds(info: TelegramFileInfo, length: Long): Boolean =
        if (info.isDownloadingCompleted) {
            info.hasLocalCopy
        } else {
            info.downloadOffset == 0L && info.downloadedPrefixSize >= length
        }

    private fun readLocal(source: File, offset: Long, size: Int): ByteArray =
        RandomAccessFile(source, "r").use { file ->
            ByteArray(size).also {
                file.seek(offset)
                file.readFully(it)
            }
        }

    private companion object {
        const val TAG = "UploadVerifier"
        const val RECHECK_INTERVAL_MS = 500L
    }
}
