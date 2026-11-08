package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.DownloadWriter
import com.drdisagree.teledrive.core.files.PartialDownload
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.data.local.entity.FileEntity
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
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCacheCleanupTest {

    private val harness = SyncHarness()
    private val database = harness.database
    private val workDir: File = Files.createTempDirectory("dl").toFile()
    private val saved = File(workDir, "saved.jpg")
    private val freed = mutableListOf<String>()

    @After
    fun tearDown() {
        harness.close()
        workDir.deleteRecursively()
    }

    @Test
    fun `a finished download frees Telegram's cached copy`() = runBlocking {
        prepare()

        val outcome = executor(writes = true).execute(database.transferDao().byId("t1")!!)

        assertEquals(TransferOutcome.Completed, outcome)
        assertEquals(listOf("r1"), freed)
        assertEquals(saved.absolutePath, database.fileDao().byId("f1")!!.localPath)
    }

    @Test
    fun `a download that could not be saved keeps the cached copy`() = runBlocking {
        prepare()

        executor(writes = false).execute(database.transferDao().byId("t1")!!)

        assertTrue(freed.isEmpty())
    }

    private suspend fun prepare() {
        database.fileDao().upsert(
            FileEntity(
                id = "f1",
                folderId = null,
                name = "a.jpg",
                sizeBytes = 3,
                mimeType = "image/jpeg",
                category = FileCategory.IMAGE,
                localPath = null,
                contentHash = null,
                chatId = 1,
                messageId = 10,
                remoteFileId = "r1",
                remoteUniqueId = "u1",
                backupState = BackupState.BACKED_UP,
                createdAt = 1,
                modifiedAt = 1,
                addedAt = 1
            )
        )
        database.transferDao().upsert(
            TransferEntity(
                id = "t1",
                type = TransferType.DOWNLOAD,
                fileId = "f1",
                displayName = "a.jpg",
                localPath = null,
                chatId = 1,
                messageId = 10,
                remoteFileId = "r1",
                sizeBytes = 3,
                state = TransferState.RUNNING,
                createdAt = 1,
                updatedAt = 1
            )
        )
    }

    private fun executor(writes: Boolean): TransferExecutor {
        val settings = FakeSettings(UserPreferences())
        val codec = ManifestCodec(StreamCrypto(), unused())
        val telegram = object : TelegramClient by unused() {
            override fun downloadDocument(remoteFileId: String): Flow<TelegramDownloadEvent> {
                val file = File(workDir, "tdlib-$remoteFileId").apply { writeBytes(byteArrayOf(1, 2, 3)) }
                return flowOf(TelegramDownloadEvent.Completed(file.absolutePath, file.length()))
            }

            override suspend fun deleteLocalCopy(remoteFileId: String) {
                freed += remoteFileId
            }
        }
        val writer = object : DownloadWriter {
            override fun write(
                fileName: String,
                mimeType: String,
                folderPath: String?,
                body: (OutputStream) -> Unit
            ): String? {
                if (!writes) return null
                saved.outputStream().use(body)
                return saved.absolutePath
            }

            override fun openPartial(
                fileName: String,
                mimeType: String,
                folderPath: String?,
                token: String?
            ): PartialDownload? = error("not used")

            override fun freeBytes(folderPath: String?): Long? = null
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
