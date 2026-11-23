package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import com.drdisagree.teledrive.testing.unused
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadVerifierTest {

    private val crypto = StreamCrypto()
    private val workDir: File = Files.createTempDirectory("verify").toFile()
    private val original = Random(7).nextBytes(3 * 1024 * 1024 + 99)
    private val source = File(workDir, "video.mp4").apply { writeBytes(original) }
    private val key = ByteArray(32) { it.toByte() }

    private var stored = ByteArray(0)
    private var reportedSize = 0L
    private var arrives = true
    private var requested = false

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `an encrypted part that decrypts to the local bytes is verified`() = runBlocking {
        val piece = store(offset = 1024 * 1024, size = 2L * 1024 * 1024, encrypt = true)

        assertEquals(UploadCheck.VERIFIED, verifier().verify(source, listOf(piece), true))
    }

    @Test
    fun `an encrypted part sealed with another key is damaged`() = runBlocking {
        val piece = store(offset = 0, size = 1024 * 1024, encrypt = true)

        val otherKey = ByteArray(32) { 9 }
        assertEquals(
            UploadCheck.DAMAGED,
            verifier(otherKey).verify(source, listOf(piece), true)
        )
    }

    @Test
    fun `a part stored with the wrong size is damaged`() = runBlocking {
        val piece = store(offset = 0, size = 1024 * 1024, encrypt = false)
        reportedSize -= 1

        assertEquals(UploadCheck.DAMAGED, verifier().verify(source, listOf(piece), false))
    }

    @Test
    fun `a plain part holding bytes from the wrong offset is damaged`() = runBlocking {
        val piece = store(offset = 0, size = 1024 * 1024, encrypt = false)
            .copy(plainOffset = 2L * 1024 * 1024)

        assertEquals(UploadCheck.DAMAGED, verifier().verify(source, listOf(piece), false))
    }

    @Test
    fun `a plain part matching the local bytes is verified`() = runBlocking {
        val piece = store(offset = 2L * 1024 * 1024, size = 1024 * 1024 + 99, encrypt = false)

        assertEquals(UploadCheck.VERIFIED, verifier().verify(source, listOf(piece), false))
    }

    @Test
    fun `a part that cannot be read back is unverified, not damaged`() = runBlocking {
        val piece = store(offset = 0, size = 1024 * 1024, encrypt = true)
        arrives = false

        assertEquals(UploadCheck.UNVERIFIED, verifier().verify(source, listOf(piece), true))
    }

    private fun store(offset: Long, size: Long, encrypt: Boolean): UploadedPiece {
        val plain = original.copyOfRange(offset.toInt(), (offset + size).toInt())
        stored = if (encrypt) {
            ByteArrayOutputStream().also { out ->
                crypto.encryptStream(key, plain.inputStream(), out)
            }.toByteArray()
        } else {
            plain
        }
        reportedSize = stored.size.toLong()
        return UploadedPiece(1, 10, offset, size, stored.size.toLong())
    }

    private fun verifier(contentKey: ByteArray = key) = UploadVerifier(
        telegramClient = telegram,
        streamCrypto = crypto,
        wrappedKeyRepository = object : WrappedKeyRepository by unused() {
            override fun get(name: String) = contentKey
        },
        fetchTimeout = 300.milliseconds
    )

    private val telegram = object : TelegramClient by unused() {
        override suspend fun getDocument(chatId: Long, messageId: Long) = RemoteDocument(
            chatId = chatId,
            messageId = messageId,
            remoteFileId = "remote",
            uniqueFileId = "unique",
            fileName = "part",
            mimeType = "application/octet-stream",
            sizeBytes = reportedSize,
            caption = "",
            dateSeconds = 1
        )

        override suspend fun resolveFile(remoteFileId: String) = info()

        override suspend fun getFileInfo(fileId: Int) = info()

        override suspend fun requestFileRange(fileId: Int, offset: Long, limit: Long) {
            requested = true
        }

        override suspend fun readFilePart(fileId: Int, offset: Long, count: Long): ByteArray =
            stored.copyOfRange(offset.toInt(), minOf(stored.size.toLong(), offset + count).toInt())

        override fun fileUpdates(fileId: Int): Flow<TelegramFileInfo> = emptyFlow()

        override suspend fun cancelFileDownload(fileId: Int) = Unit

        override suspend fun deleteLocalCopy(remoteFileId: String) = Unit
    }

    private fun info() = TelegramFileInfo(
        fileId = 5,
        sizeBytes = stored.size.toLong(),
        localPath = null,
        isDownloadingCompleted = false,
        isDownloadingActive = arrives && requested,
        downloadOffset = 0,
        downloadedPrefixSize = if (arrives && requested) stored.size.toLong() else 0
    )
}
