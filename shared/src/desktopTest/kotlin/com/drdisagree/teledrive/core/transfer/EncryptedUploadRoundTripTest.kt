package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.DownloadWriter
import com.drdisagree.teledrive.core.files.FileImporter
import com.drdisagree.teledrive.core.files.PartialDownload
import com.drdisagree.teledrive.core.media.MediaPart
import com.drdisagree.teledrive.core.media.ThumbnailStore
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramFileInfo
import com.drdisagree.teledrive.core.telegram.TelegramLimits
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
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
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedUploadRoundTripTest {

    private val harness = SyncHarness()
    private val database = harness.database
    private val workDir: File = Files.createTempDirectory("roundtrip").toFile()
    private val original = Random(132).nextBytes(3 * 1024 * 1024 + 17)
    private val source = File(workDir, "song.mp3").apply { writeBytes(original) }
    private val uploaded = File(workDir, "uploaded.tde")
    private val saved = File(workDir, "saved.mp3")
    private val key = ByteArray(32) { it.toByte() }
    private val deleted = mutableListOf<Long>()
    private var corruptUpload = false

    @After
    fun tearDown() {
        harness.close()
        workDir.deleteRecursively()
    }

    @Test
    fun `a file uploaded encrypted downloads as the original on the same device`() =
        runBlocking {
            record()
            transfer("up", TransferType.UPLOAD)
            val executor = executor()

            assertEquals(TransferOutcome.Completed, executor.execute(byId("up")))
            assertTrue(database.fileDao().byId("f1")!!.isEncrypted)

            database.fileDao().setLocalPath("f1", null)
            transfer("down", TransferType.DOWNLOAD)
            assertEquals(TransferOutcome.Completed, executor.execute(byId("down")))

            assertArrayEquals(original, saved.readBytes())
        }

    @Test
    fun `an encrypted upload that does not read back is removed and the local file kept`() =
        runBlocking {
            corruptUpload = true
            record()
            transfer("up", TransferType.UPLOAD)

            val outcome = executor().execute(byId("up"))

            assertTrue((outcome as TransferOutcome.Failed).damaged)
            assertEquals(listOf(10L), deleted)
            val row = database.fileDao().byId("f1")!!
            assertNull(row.messageId)
            assertEquals(source.absolutePath, row.localPath)
            assertTrue(source.isFile)
        }

    private fun storedInfo() = TelegramFileInfo(
        fileId = 5,
        sizeBytes = uploaded.length(),
        localPath = null,
        isDownloadingCompleted = false,
        isDownloadingActive = true,
        downloadOffset = 0,
        downloadedPrefixSize = uploaded.length()
    )

    private suspend fun byId(id: String) = database.transferDao().byId(id)!!

    private suspend fun record() {
        database.fileDao().upsert(
            FileEntity(
                id = "f1",
                folderId = null,
                name = "song.mp3",
                sizeBytes = source.length(),
                mimeType = "audio/mpeg",
                category = FileCategory.AUDIO,
                localPath = source.absolutePath,
                contentHash = null,
                chatId = 1,
                messageId = null,
                remoteFileId = null,
                remoteUniqueId = null,
                backupState = BackupState.QUEUED,
                createdAt = 1,
                modifiedAt = 1,
                addedAt = 1
            )
        )
    }

    private suspend fun transfer(id: String, type: TransferType) {
        database.transferDao().upsert(
            TransferEntity(
                id = id,
                type = type,
                fileId = "f1",
                displayName = "song.mp3",
                localPath = null,
                chatId = 1,
                messageId = if (type == TransferType.DOWNLOAD) 10 else null,
                remoteFileId = if (type == TransferType.DOWNLOAD) "r10" else null,
                sizeBytes = source.length(),
                state = TransferState.RUNNING,
                createdAt = 1,
                updatedAt = 1
            )
        )
    }

    private fun executor(): TransferExecutor {
        val settings = FakeSettings(
            UserPreferences(storageChatId = 1, encryptFiles = true, keyBackupCreated = true)
        )
        val keys = object : WrappedKeyRepository {
            override fun getOrCreate(name: String, sizeBytes: Int) = key
            override fun wasRecreated(name: String) = false
            override fun get(name: String) = key
            override fun exists(name: String) = true
            override fun store(name: String, key: ByteArray) = Unit
        }
        val codec = ManifestCodec(StreamCrypto(), keys)
        val telegram = object : TelegramClient by unused() {
            override suspend fun getLimits() = TelegramLimits.REGULAR

            override fun uploadDocument(
                chatId: Long,
                localPath: String,
                fileName: String,
                mimeType: String,
                caption: String,
                thumbnailPath: String?
            ): Flow<TelegramUploadEvent> {
                File(localPath).copyTo(uploaded, overwrite = true)
                if (corruptUpload) {
                    val bytes = uploaded.readBytes()
                    bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
                    bytes[40] = (bytes[40] + 1).toByte()
                    uploaded.writeBytes(bytes)
                }
                return flowOf(
                    TelegramUploadEvent.Completed(
                        RemoteDocument(
                            chatId = chatId,
                            messageId = 10,
                            remoteFileId = "r10",
                            uniqueFileId = "u10",
                            fileName = fileName,
                            mimeType = mimeType,
                            sizeBytes = uploaded.length(),
                            caption = caption,
                            dateSeconds = 1
                        )
                    )
                )
            }

            override fun downloadDocument(remoteFileId: String): Flow<TelegramDownloadEvent> =
                flowOf(TelegramDownloadEvent.Completed(uploaded.absolutePath, uploaded.length()))

            override suspend fun deleteLocalCopy(remoteFileId: String) = Unit

            override suspend fun getDocument(chatId: Long, messageId: Long) = RemoteDocument(
                chatId = chatId,
                messageId = messageId,
                remoteFileId = "r10",
                uniqueFileId = "u10",
                fileName = "uploaded.tde",
                mimeType = "application/octet-stream",
                sizeBytes = uploaded.length(),
                caption = "",
                dateSeconds = 1
            ).takeIf { messageId !in deleted }

            override suspend fun resolveFile(remoteFileId: String) = storedInfo()

            override suspend fun getFileInfo(fileId: Int) = storedInfo()

            override suspend fun requestFileRange(fileId: Int, offset: Long, limit: Long) = Unit

            override suspend fun readFilePart(fileId: Int, offset: Long, count: Long): ByteArray =
                uploaded.readBytes().copyOfRange(offset.toInt(), (offset + count).toInt())

            override fun fileUpdates(fileId: Int): Flow<TelegramFileInfo> = emptyFlow()

            override suspend fun cancelFileDownload(fileId: Int) = Unit

            override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>) {
                deleted += messageIds
            }
        }
        val thumbnails = object : ThumbnailStore {
            override suspend fun thumbnailBytes(fileId: String): ByteArray? = null
            override suspend fun uploadThumbnailFile(fileId: String): File? = null
            override suspend fun storeFromDecrypted(fileId: String, plain: File) = Unit
            override suspend fun storeFromStream(
                fileId: String,
                parts: List<MediaPart>,
                encrypted: Boolean
            ) = Unit
        }
        val writer = object : DownloadWriter {
            override fun write(
                fileName: String,
                mimeType: String,
                folderPath: String?,
                body: (OutputStream) -> Unit
            ): String? = runCatching {
                saved.outputStream().use(body)
                saved.absolutePath
            }.getOrNull()

            override fun openPartial(
                fileName: String,
                mimeType: String,
                folderPath: String?,
                token: String?
            ): PartialDownload? = error("not used")

            override fun freeBytes(folderPath: String?): Long? = null
        }
        val importer = object : FileImporter by unused() {
            override fun isStaged(path: String) = false
        }
        val paths = object : AppStoragePaths by unused() {
            override val filesDir: File = workDir
            override val cacheDir: File = workDir
        }
        val dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.IO
            override val default = Dispatchers.Default
        }
        val icons = ApkIconUploader(unused(), unused(), StreamCrypto(), unused(), unused())
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
            thumbnailStore = thumbnails,
            streamCrypto = StreamCrypto(),
            wrappedKeyRepository = keys,
            downloadWriter = writer,
            fileImporter = importer,
            settingsRepository = settings,
            filePartDao = database.filePartDao(),
            partUploader = PartUploader(
                paths, telegram, database.filePartDao(), codec, StreamCrypto(), keys,
                thumbnails, dispatchers, icons
            ),
            partDownloader = PartDownloader(
                telegram, database.filePartDao(), StreamCrypto(), keys, dispatchers
            ),
            apkIconUploader = icons,
            localCopyDeleter = unused(),
            uploadVerifier = UploadVerifier(telegram, StreamCrypto(), keys)
        )
    }
}
