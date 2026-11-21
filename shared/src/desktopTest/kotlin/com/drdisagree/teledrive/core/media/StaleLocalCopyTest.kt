package com.drdisagree.teledrive.core.media

import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import com.drdisagree.teledrive.testing.unused
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleLocalCopyTest {

    private val bytes = ByteArray(4096) { it.toByte() }
    private var requested = false

    private val telegram = object : TelegramClient by unused() {
        private fun info() = TelegramFileInfo(
            fileId = 7,
            sizeBytes = bytes.size.toLong(),
            localPath = if (requested) null else "/gone/uploaded-part.bin",
            isDownloadingCompleted = !requested,
            isDownloadingActive = requested,
            downloadOffset = 0,
            downloadedPrefixSize = bytes.size.toLong()
        )

        override suspend fun resolveFile(remoteFileId: String) = info()

        override suspend fun getFileInfo(fileId: Int) = info()

        override suspend fun requestFileRange(fileId: Int, offset: Long, limit: Long) {
            requested = true
        }

        override suspend fun readFilePart(fileId: Int, offset: Long, count: Long): ByteArray {
            if (!requested) error("File is not inside the cache")
            return bytes.copyOfRange(offset.toInt(), (offset + count).toInt())
        }

        override fun fileUpdates(fileId: Int): Flow<TelegramFileInfo> = emptyFlow()

        override suspend fun cancelFileDownload(fileId: Int) = Unit
    }

    @Test
    fun `a file uploaded from this device whose local copy is gone is downloaded again`() =
        runBlocking {
            val source = TelegramMediaByteSource(telegram, "remote")

            val read = source.read(0, 1024)

            assertTrue(requested)
            assertArrayEquals(bytes.copyOfRange(0, 1024), read)
        }

    @Test
    fun `a split part uploaded from this device whose local copy is gone is downloaded again`() =
        runBlocking {
            val parts = listOf(MediaPart("remote", 0, bytes.size.toLong()))
            val source = PartedMediaByteSource(telegram, StreamCrypto(), parts, false, null)

            val read = source.read(512, 1024)

            assertTrue(requested)
            assertArrayEquals(bytes.copyOfRange(512, 1536), read)
        }
}
