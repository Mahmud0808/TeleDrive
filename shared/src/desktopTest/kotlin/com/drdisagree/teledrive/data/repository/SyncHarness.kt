package com.drdisagree.teledrive.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.testing.unused
import java.io.File
import kotlinx.coroutines.Dispatchers

internal class SyncHarness : AutoCloseable {
    private val dbFile = File.createTempFile("teledrive-sync", ".db")
    val database: TeleDriveDatabase = Room.databaseBuilder<TeleDriveDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    val settings = FakeSettings(UserPreferences(storageChatId = TEST_CHAT))
    val telegram = FakeTelegram()
    val scheduler = CountingScheduler()
    val codec = ManifestCodec(StreamCrypto(), unused<WrappedKeyRepository>())
    val activeChannel = ActiveChannel(settings)
    val folderPaths = FolderPathResolver(
        database.folderDao(),
        activeChannel,
        database.folderTombstoneDao()
    )
    val storagePaths = FakeStoragePaths()
    val folderState = FolderStateSynchronizer(
        storagePaths,
        telegram,
        database.folderDao(),
        database.folderTombstoneDao(),
        settings,
        StreamCrypto(),
        unused<WrappedKeyRepository>()
    )

    fun newSync() = SyncRepositoryImpl(
        telegramClient = telegram,
        fileDao = database.fileDao(),
        manifestCodec = codec,
        pendingDeleteDao = database.pendingDeleteDao(),
        filePartDao = database.filePartDao(),
        folderDao = database.folderDao(),
        folderPathResolver = folderPaths,
        activeChannel = activeChannel,
        channelOwnership = ChannelOwnership(
            database.fileDao(),
            database.folderDao(),
            database.exclusionDao()
        ),
        folderStateSynchronizer = folderState,
        publishScheduler = scheduler,
        settingsRepository = settings,
        database = database
    )

    fun caption(manifest: RemoteFileManifest) = codec.encode(manifest, encrypt = false)

    override fun close() {
        database.close()
        dbFile.delete()
        telegram.cleanUp()
        storagePaths.cacheDir.deleteRecursively()
    }
}
