package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.DownloadWriter
import com.drdisagree.teledrive.core.files.FilePartialDownload
import com.drdisagree.teledrive.core.files.Hashing
import com.drdisagree.teledrive.core.files.PartialDownload
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.FilePartEntity
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.repository.ActiveChannel
import com.drdisagree.teledrive.data.repository.FakeSettings
import com.drdisagree.teledrive.data.repository.FolderPathResolver
import com.drdisagree.teledrive.data.repository.SyncHarness
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.testing.unused
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeDownloadTest {

    private val harness = SyncHarness()
    private val database = harness.database
    private val workDir: File = Files.createTempDirectory("large").toFile()
    private val destination = File(workDir, "Downloads").apply { mkdirs() }
    private val contents = listOf(ByteArray(400) { 4 }, ByteArray(100) { 5 })
    private val whole = contents.reduce { a, b -> a + b }
    private var free: Long? = null

    @After
    fun tearDown() {
        harness.close()
        workDir.deleteRecursively()
    }

    @Test
    fun `a destination without room fails before downloading anything`() = runBlocking {
        prepare(hash = null)
        free = 100

        val outcome = executor().execute(transfer())

        assertEquals(TransferOutcome.Failed("destinationFull"), outcome)
        assertTrue(downloaded.isEmpty())
    }

    @Test
    fun `a finished download is checked and renamed in place`() = runBlocking {
        prepare(hash = Hashing.sha256(ByteArrayInputStream(whole)))

        val outcome = executor().execute(transfer())

        assertEquals(TransferOutcome.Completed, outcome)
        val saved = File(database.fileDao().byId("f1")!!.localPath!!)
        assertEquals("big.vhdx", saved.name)
        assertEquals(destination, saved.parentFile)
        assertEquals(whole.toList(), saved.readBytes().toList())
        assertNull(database.transferDao().byId("t1")!!.localPath)
    }

    @Test
    fun `a download that does not match the original is thrown away`() = runBlocking {
        prepare(hash = "0".repeat(64))

        val outcome = executor().execute(transfer())

        assertEquals(TransferOutcome.Failed("getVerifyFailed"), outcome)
        assertTrue(destination.listFiles().orEmpty().isEmpty())
        assertNull(database.fileDao().byId("f1")!!.localPath)
    }

    private val downloaded = mutableListOf<String>()

    private suspend fun prepare(hash: String?) {
        database.fileDao().upsert(
            FileEntity(
                id = "f1",
                folderId = null,
                name = "big.vhdx",
                sizeBytes = whole.size.toLong(),
                mimeType = "application/octet-stream",
                category = FileCategory.OTHER,
                localPath = null,
                contentHash = hash,
                chatId = 1,
                messageId = 10,
                remoteFileId = "r0",
                remoteUniqueId = "u0",
                backupState = BackupState.BACKED_UP,
                partCount = contents.size,
                createdAt = 1,
                modifiedAt = 1,
                addedAt = 1
            )
        )
        var offset = 0L
        contents.forEachIndexed { index, bytes ->
            database.filePartDao().upsert(
                FilePartEntity(
                    fileId = "f1",
                    partIndex = index,
                    chatId = 1,
                    messageId = 10L + index,
                    remoteFileId = "r$index",
                    remoteUniqueId = "u$index",
                    plainOffset = offset,
                    plainSize = bytes.size.toLong(),
                    storedSize = bytes.size.toLong(),
                    uploadedAt = 1
                )
            )
            offset += bytes.size
        }
        database.transferDao().upsert(
            TransferEntity(
                id = "t1",
                type = TransferType.DOWNLOAD,
                fileId = "f1",
                displayName = "big.vhdx",
                localPath = null,
                chatId = 1,
                messageId = 10,
                remoteFileId = "r0",
                sizeBytes = whole.size.toLong(),
                state = TransferState.RUNNING,
                createdAt = 1,
                updatedAt = 1
            )
        )
    }

    private suspend fun transfer() = database.transferDao().byId("t1")!!

    private fun executor(): TransferExecutor {
        val settings = FakeSettings(UserPreferences())
        val codec = ManifestCodec(StreamCrypto(), unused())
        val telegram = object : TelegramClient by unused() {
            override fun downloadDocument(remoteFileId: String): Flow<TelegramDownloadEvent> {
                downloaded += remoteFileId
                val bytes = contents[remoteFileId.removePrefix("r").toInt()]
                val file = File(workDir, "tdlib-$remoteFileId").apply { writeBytes(bytes) }
                return flowOf(TelegramDownloadEvent.Completed(file.absolutePath, file.length()))
            }

            override suspend fun deleteLocalCopy(remoteFileId: String) {
                File(workDir, "tdlib-$remoteFileId").delete()
            }
        }
        val writer = object : DownloadWriter {
            override fun write(
                fileName: String,
                mimeType: String,
                folderPath: String?,
                body: (OutputStream) -> Unit
            ): String? = error("not used")

            override fun openPartial(
                fileName: String,
                mimeType: String,
                folderPath: String?,
                token: String?
            ): PartialDownload? = FilePartialDownload.open(destination, fileName, token)

            override fun freeBytes(folderPath: String?): Long? = free
        }
        val paths = object : AppStoragePaths by unused() {
            override val filesDir: File = workDir
        }
        val dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.IO
            override val default = Dispatchers.Default
        }
        return TransferExecutor(
            messages = namedErrorMessages(),
            storagePaths = paths,
            telegramClient = telegram,
            transferDao = database.transferDao(),
            fileDao = database.fileDao(),
            backupDao = database.backupDao(),
            manifestCodec = codec,
            folderPathResolver = FolderPathResolver(
                database.folderDao(),
                ActiveChannel(settings),
                database.folderTombstoneDao()
            ),
            thumbnailStore = unused(),
            streamCrypto = StreamCrypto(),
            wrappedKeyRepository = unused(),
            downloadWriter = writer,
            fileImporter = unused(),
            settingsRepository = settings,
            filePartDao = database.filePartDao(),
            partUploader = PartUploader(
                unused(), unused(), database.filePartDao(), codec, StreamCrypto(), unused(),
                unused(), unused<DispatcherProvider>(),
                ApkIconUploader(unused(), unused(), StreamCrypto(), unused(), unused())
            ),
            partDownloader = PartDownloader(
                telegram, database.filePartDao(), StreamCrypto(), unused(), dispatchers
            ),
            apkIconUploader = ApkIconUploader(unused(), unused(), StreamCrypto(), unused(), unused()),
            localCopyDeleter = unused()
        )
    }
}
