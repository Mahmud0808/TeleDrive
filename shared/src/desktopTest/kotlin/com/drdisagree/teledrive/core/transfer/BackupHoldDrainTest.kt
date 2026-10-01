package com.drdisagree.teledrive.core.transfer

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.network.NetworkStatus
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.repository.ActiveChannel
import com.drdisagree.teledrive.data.repository.FakeSettings
import com.drdisagree.teledrive.data.repository.FolderPathResolver
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.testing.unused
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupHoldDrainTest {

    private val dbFile = File.createTempFile("teledrive-drain", ".db")
    private val database: TeleDriveDatabase = Room.databaseBuilder<TeleDriveDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val transfers = database.transferDao()
    private val resume = RecordingBackupResumeScheduler()

    @After
    fun tearDown() {
        database.close()
        dbFile.delete()
    }

    @Test
    fun `backups wait for the charger while the user's own uploads still run`() = runBlocking {
        queue("backup", TransferType.BACKUP)
        queue("upload", TransferType.UPLOAD)
        val drainer = drainer(UserPreferences(backupChargingOnly = true), charging = false)

        drainer.drain(isStopped = { false }, onTerminalFailure = {})

        assertEquals(TransferState.QUEUED, transfers.byId("backup")!!.state)
        assertEquals(TransferState.FAILED, transfers.byId("upload")!!.state)
        assertEquals(listOf(true to false), resume.requests)
    }

    @Test
    fun `backups run once the charger is plugged in`() = runBlocking {
        queue("backup", TransferType.BACKUP)
        val drainer = drainer(UserPreferences(backupChargingOnly = true), charging = true)

        drainer.drain(isStopped = { false }, onTerminalFailure = {})

        assertEquals(TransferState.FAILED, transfers.byId("backup")!!.state)
        assertTrue(resume.requests.isEmpty())
    }

    @Test
    fun `wifi only backups wait on mobile data`() = runBlocking {
        queue("backup", TransferType.BACKUP)
        queue("download", TransferType.DOWNLOAD)
        val drainer = drainer(
            UserPreferences(backupWifiOnly = true),
            charging = true,
            network = NetworkStatus.METERED
        )

        drainer.drain(isStopped = { false }, onTerminalFailure = {})

        assertEquals(TransferState.QUEUED, transfers.byId("backup")!!.state)
        assertEquals(TransferState.FAILED, transfers.byId("download")!!.state)
        assertEquals(listOf(false to true), resume.requests)
    }

    @Test
    fun `a queue holding only waiting backups starts no work and schedules the resume`() = runBlocking {
        queue("backup", TransferType.BACKUP)
        val drainer = drainer(UserPreferences(backupChargingOnly = true), charging = false)

        assertFalse(drainer.hasRunnableWork())
        assertEquals(listOf(true to false), resume.requests)
    }

    @Test
    fun `a parked slot does not wait forever on held backups`() = runBlocking {
        queue("backup", TransferType.BACKUP)
        val drainer = drainer(
            UserPreferences(backupChargingOnly = true, transferConcurrency = 1),
            charging = false
        )

        withTimeout(20.seconds) { drainer.drain(isStopped = { false }, onTerminalFailure = {}) }

        assertEquals(TransferState.QUEUED, transfers.byId("backup")!!.state)
    }

    private suspend fun queue(id: String, type: TransferType) {
        transfers.upsert(
            TransferEntity(
                id = id,
                type = type,
                fileId = null,
                displayName = id,
                localPath = null,
                chatId = null,
                messageId = null,
                remoteFileId = null,
                sizeBytes = 1,
                state = TransferState.QUEUED,
                createdAt = 1,
                updatedAt = 1
            )
        )
    }

    private fun drainer(
        prefs: UserPreferences,
        charging: Boolean,
        network: NetworkStatus = NetworkStatus.UNMETERED
    ): TransferQueueDrainer {
        val settings = FakeSettings(prefs.copy(transferRetryCount = 0))
        val codec = ManifestCodec(StreamCrypto(), unused())
        val executor = TransferExecutor(
            messages = namedErrorMessages(),
            storagePaths = unused(),
            telegramClient = unused(),
            transferDao = transfers,
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
            downloadWriter = unused(),
            fileImporter = unused(),
            settingsRepository = settings,
            filePartDao = database.filePartDao(),
            partUploader = PartUploader(
                unused(), unused(), database.filePartDao(), codec, StreamCrypto(), unused(),
                unused(), unused<DispatcherProvider>(),
                ApkIconUploader(unused(), unused(), StreamCrypto(), unused(), unused())
            ),
            partDownloader = PartDownloader(
                unused(), unused(), database.filePartDao(), StreamCrypto(), unused(), unused()
            ),
            apkIconUploader = ApkIconUploader(unused(), unused(), StreamCrypto(), unused(), unused()),
            localCopyDeleter = unused()
        )
        return TransferQueueDrainer(
            transferDao = transfers,
            fileDao = database.fileDao(),
            transferExecutor = executor,
            backupSessionTracker = NoOpBackupSessionTracker(),
            settingsRepository = settings,
            networkMonitor = FakeNetworkMonitor(network),
            powerMonitor = FakePowerMonitor(charging),
            backupResumeScheduler = resume
        )
    }
}
