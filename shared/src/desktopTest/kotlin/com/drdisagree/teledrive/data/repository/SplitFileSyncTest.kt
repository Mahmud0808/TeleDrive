package com.drdisagree.teledrive.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.publish.PublishScheduler
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.RemoteDocumentPage
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.transfer.FileParts
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitFileSyncTest {

    private val dbFile = File.createTempFile("teledrive-sync", ".db")
    private val database: TeleDriveDatabase = Room.databaseBuilder<TeleDriveDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val settings = FakeSettings(UserPreferences(storageChatId = CHAT))
    private val telegram = FakeTelegram()
    private val scheduler = CountingScheduler()
    private val codec = ManifestCodec(StreamCrypto(), unused<WrappedKeyRepository>())
    private val activeChannel = ActiveChannel(settings)
    private val folderPaths = FolderPathResolver(database.folderDao(), activeChannel)

    private val sync = SyncRepositoryImpl(
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
        folderStateSynchronizer = FolderStateSynchronizer(
            unused<AppStoragePaths>(),
            telegram,
            database.folderDao(),
            settings,
            StreamCrypto(),
            unused<WrappedKeyRepository>()
        ),
        publishScheduler = scheduler,
        settingsRepository = settings,
        database = database
    )

    @After
    fun tearDown() {
        database.close()
        dbFile.delete()
    }

    @Test
    fun `fresh install restores part 0 of a split file whose caption lost its part fields`() =
        runBlocking {
            telegram.documents = splitFile(damagedFirstPart = true)

            sync.fullResync()

            val parts = database.filePartDao().partsOf(SPLIT_ID)
            assertEquals(listOf(0, 1, 2), parts.map { it.partIndex })
            val first = parts.first()
            assertEquals(10L, first.messageId)
            assertEquals(0L, first.plainOffset)
            assertEquals(FileParts.PART_SIZE, first.plainSize)

            val row = database.fileDao().byId(SPLIT_ID)
            assertNotNull(row)
            assertEquals(10L, row!!.messageId)
            assertEquals(3, row.partCount)
            assertTrue(row.pendingPublish)
            assertEquals(1, scheduler.kicks)
        }

    @Test
    fun `later parts never recreate the folder a split file was moved out of`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = true)

        sync.fullResync()

        val names = database.folderDao().allFolders(CHAT).map { it.name }
        assertEquals(listOf("New"), names)
        assertEquals(NEW_FOLDER_ID, database.fileDao().byId(SPLIT_ID)!!.folderId)
    }

    @Test
    fun `a repaired part 0 is not repaired again on later syncs`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = true)

        sync.fullResync()
        sync.fullResync()
        assertEquals(1, scheduler.kicks)

        database.fileDao().clearPendingPublish(SPLIT_ID)
        sync.fullResync()
        assertEquals(1, scheduler.kicks)
        assertFalse(database.fileDao().byId(SPLIT_ID)!!.pendingPublish)
    }

    @Test
    fun `a fixed caption ends the repair for good`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = true)
        sync.fullResync()
        database.fileDao().clearPendingPublish(SPLIT_ID)

        telegram.documents = splitFile(damagedFirstPart = false)
        val fresh = SyncRepositoryImpl(
            telegram, database.fileDao(), codec, database.pendingDeleteDao(),
            database.filePartDao(), database.folderDao(), folderPaths, activeChannel,
            ChannelOwnership(database.fileDao(), database.folderDao(), database.exclusionDao()),
            FolderStateSynchronizer(
                unused<AppStoragePaths>(), telegram, database.folderDao(), settings,
                StreamCrypto(), unused<WrappedKeyRepository>()
            ),
            scheduler, settings, database
        )
        fresh.fullResync()

        assertEquals(1, scheduler.kicks)
        assertFalse(database.fileDao().byId(SPLIT_ID)!!.pendingPublish)
        assertEquals(listOf(0, 1, 2), database.filePartDao().partsOf(SPLIT_ID).map { it.partIndex })
    }

    @Test
    fun `a healthy split file is left alone`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = false)

        sync.fullResync()

        assertEquals(listOf(0, 1, 2), database.filePartDao().partsOf(SPLIT_ID).map { it.partIndex })
        assertFalse(database.fileDao().byId(SPLIT_ID)!!.pendingPublish)
        assertEquals(0, scheduler.kicks)
    }

    @Test
    fun `an ordinary file gains no parts and no repair`() = runBlocking {
        telegram.documents = listOf(
            document(20, caption(manifest("single", "photo.jpg", 4_000_000, "", null)))
        )

        sync.fullResync()

        val row = database.fileDao().byId("single")
        assertNotNull(row)
        assertEquals(0, row!!.partCount)
        assertFalse(row.pendingPublish)
        assertTrue(database.filePartDao().partsOf("single").isEmpty())
        assertEquals(0, scheduler.kicks)
    }

    @Test
    fun `republishing a split file keeps its part fields and icon`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = false)
        sync.fullResync()
        val publisher = FileManifestPublisher(telegram, codec, folderPaths, database.filePartDao())
        val row = database.fileDao().byId(SPLIT_ID)!!.copy(name = "renamed.mkv", iconFileId = "icon-1")

        publisher.publish(row)

        val written = codec.decode(telegram.editedCaptions.getValue(10L))!!
        assertTrue(written.isPart)
        assertEquals(0, written.partIndex)
        assertEquals(3, written.partCount)
        assertEquals(0L, written.partOffset)
        assertEquals(FileParts.PART_SIZE, written.partSize)
        assertEquals("renamed.mkv", written.name)
        assertEquals("icon-1", written.iconFileId)
    }

    @Test
    fun `republishing an ordinary file writes no part fields`() = runBlocking {
        telegram.documents = listOf(
            document(20, caption(manifest("single", "photo.jpg", 4_000_000, "", null)))
        )
        sync.fullResync()
        val publisher = FileManifestPublisher(telegram, codec, folderPaths, database.filePartDao())

        publisher.publish(database.fileDao().byId("single")!!)

        val written = codec.decode(telegram.editedCaptions.getValue(20L))!!
        assertFalse(written.isPart)
        assertEquals(0, written.partCount)
        assertNull(written.iconFileId)
    }

    private fun splitFile(damagedFirstPart: Boolean): List<RemoteDocument> {
        val whole = manifest(SPLIT_ID, "movie.mkv", SPLIT_SIZE, "New", NEW_FOLDER_ID)
        val stale = whole.copy(folderPath = "Old", folderId = "old-folder")
        val part0 = if (damagedFirstPart) whole else FileParts.asFirstPart(whole, 3)
        return listOf(
            document(12, caption(partOf(stale, 2))),
            document(11, caption(partOf(stale, 1))),
            document(10, caption(part0))
        )
    }

    private fun partOf(manifest: RemoteFileManifest, index: Int) = manifest.copy(
        version = RemoteFileManifest.PART_VERSION,
        partCount = 3,
        partIndex = index,
        partOffset = FileParts.offsetOf(index),
        partSize = FileParts.sizeOf(index, SPLIT_SIZE)
    )

    private fun manifest(
        id: String,
        name: String,
        size: Long,
        folderPath: String,
        folderId: String?
    ) = RemoteFileManifest(
        fileId = id,
        name = name,
        folderPath = folderPath,
        folderId = folderId,
        mimeType = "video/x-matroska",
        sizeBytes = size,
        createdAt = 1_000,
        modifiedAt = 2_000
    )

    private fun caption(manifest: RemoteFileManifest) = codec.encode(manifest, encrypt = false)

    private fun document(messageId: Long, caption: String) = RemoteDocument(
        chatId = CHAT,
        messageId = messageId,
        remoteFileId = "remote-$messageId",
        uniqueFileId = "unique-$messageId",
        fileName = "file-$messageId",
        mimeType = "application/octet-stream",
        sizeBytes = FileParts.PART_SIZE,
        caption = caption,
        dateSeconds = 100
    )

    private class FakeTelegram : TelegramClient by unused() {
        var documents: List<RemoteDocument> = emptyList()
        val editedCaptions = mutableMapOf<Long, String>()

        override suspend fun ensureStorageChat(knownChatId: Long?): Long = CHAT

        override suspend fun fetchDocuments(
            chatId: Long,
            fromMessageId: Long,
            limit: Int
        ): RemoteDocumentPage {
            val newestFirst = documents.sortedByDescending { it.messageId }
                .filter { fromMessageId == 0L || it.messageId < fromMessageId }
                .take(limit)
            return RemoteDocumentPage(
                documents = newestFirst,
                nextFromMessageId = newestFirst.lastOrNull()?.messageId ?: 0L
            )
        }

        override suspend fun editCaption(chatId: Long, messageId: Long, caption: String) {
            editedCaptions[messageId] = caption
        }
    }

    private class FakeSettings(initial: UserPreferences) : SettingsRepository by unused() {
        private val state = MutableStateFlow(initial)
        override val preferences: Flow<UserPreferences> = state

        override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
            state.value = transform(state.value)
        }
    }

    private class CountingScheduler : PublishScheduler {
        var kicks = 0
        override fun kick() {
            kicks++
        }
    }

    private companion object {
        const val CHAT = 77L
        const val SPLIT_ID = "split"
        const val NEW_FOLDER_ID = "new-folder"
        val SPLIT_SIZE = FileParts.PART_SIZE * 2 + 1_000
    }
}

private inline fun <reified T : Any> unused(): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java)
) { _, method, _ -> throw UnsupportedOperationException(method.name) } as T
