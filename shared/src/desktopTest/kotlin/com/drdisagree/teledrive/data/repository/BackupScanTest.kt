package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.StorageInspector
import com.drdisagree.teledrive.core.media.MediaInfo
import com.drdisagree.teledrive.core.media.MediaMetadataExtractor
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramLimits
import com.drdisagree.teledrive.core.transfer.CountingBackupSessionTracker
import com.drdisagree.teledrive.core.transfer.TransferScheduler
import com.drdisagree.teledrive.domain.model.BackupTrigger
import com.drdisagree.teledrive.domain.model.Exclusion
import com.drdisagree.teledrive.domain.model.ExclusionType
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.ExclusionRepository
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.usecase.DecideBackupActionUseCase
import com.drdisagree.teledrive.domain.usecase.EvaluateExclusionsUseCase
import com.drdisagree.teledrive.domain.usecase.ValidateUploadUseCase
import com.drdisagree.teledrive.testing.unused
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScanTest {

    private val harness = SyncHarness()
    private val database = harness.database
    private val root: File = Files.createTempDirectory("storage").toFile()
    private val media = File(root, "WhatsApp/Media").apply { mkdirs() }
    private var kicks = 0

    @After
    fun tearDown() {
        harness.close()
        root.deleteRecursively()
    }

    @Test
    fun `a scan creates drive folders only for files it backs up`() = runBlocking {
        File(media, "Images").mkdirs()
        File(media, "Images/a.jpg").writeText("a")
        File(media, "Private").mkdirs()
        File(media, "Private/b.jpg").writeText("b")

        backups(exclude = File(media, "Private").absolutePath).startBackup(BackupTrigger.MANUAL)

        val names = database.folderDao().allFolders(TEST_CHAT).map { it.name }
        assertTrue(names.any { it.endsWith("Images") })
        assertTrue(names.none { "Private" in it })
    }

    @Test
    fun `queueing files that are already queued still wakes the queue`() = runBlocking {
        File(media, "a.jpg").writeText("a")
        backups(exclude = null).startBackup(BackupTrigger.MANUAL)
        val fileId = database.fileDao().byLocalPath(File(media, "a.jpg").absolutePath, TEST_CHAT)!!.id
        val before = kicks

        transfers().enqueueBackupBatch(listOf(fileId), "later")

        assertEquals(before + 1, kicks)
    }

    private fun transfers() = TransferRepositoryImpl(
        transferDao = database.transferDao(),
        fileDao = database.fileDao(),
        telegramClient = object : TelegramClient by unused() {
            override suspend fun getLimits() = TelegramLimits.REGULAR
        },
        transferScheduler = object : TransferScheduler {
            override fun kick(allowMetered: Boolean) {
                kicks++
            }

            override fun rekick(allowMetered: Boolean) = kick(allowMetered)
        },
        settingsRepository = harness.settings,
        validateUpload = ValidateUploadUseCase(),
        storageInspector = StorageInspector(paths),
        backupSessionTracker = CountingBackupSessionTracker(
            database.transferDao(),
            database.backupDao()
        )
    )

    private val paths = object : AppStoragePaths by unused() {
        override val filesDir: File = root
        override val externalStorageRoot: File = root
    }

    private fun backups(exclude: String?) = BackupRepositoryImpl(
        backupDao = database.backupDao(),
        fileDao = database.fileDao(),
        transferDao = database.transferDao(),
        transferRepository = transfers(),
        backupSessionTracker = CountingBackupSessionTracker(
            database.transferDao(),
            database.backupDao()
        ),
        exclusionRepository = object : ExclusionRepository by unused() {
            override suspend fun getEnabled() = listOfNotNull(
                exclude?.let { Exclusion("x", ExclusionType.FOLDER_PATH, it, true, 1) }
            )
        },
        settingsRepository = harness.settings,
        decideBackupAction = DecideBackupActionUseCase(EvaluateExclusionsUseCase()),
        mediaMetadataExtractor = object : MediaMetadataExtractor {
            override fun extract(file: File, mimeType: String) = MediaInfo(null, null, null)
        },
        folderPathResolver = harness.folderPaths,
        fileRepository = object : FileRepository by unused() {
            override suspend fun reviveTrashedCopy(
                localPath: String,
                folderId: suspend () -> String?
            ) = null
        },
        channelRepository = object : ChannelRepository by unused() {
            override suspend fun backupFolders(chatId: Long) = setOf(media.absolutePath)
        },
        storagePaths = paths,
        dispatchers = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.IO
            override val default = Dispatchers.Default
        }
    )
}
