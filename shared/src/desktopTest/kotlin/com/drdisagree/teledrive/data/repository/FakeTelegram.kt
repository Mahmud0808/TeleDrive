package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.telegram.MessageChange
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.RemoteDocumentPage
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.testing.unused
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

internal class FakeTelegram : TelegramClient by unused() {
    var documents: List<RemoteDocument> = emptyList()
    val editedCaptions = mutableMapOf<Long, String>()
    val uploads = mutableListOf<Pair<String, String>>()
    val deletedMessages = mutableListOf<Long>()
    var failDownloads = false
    val changes = MutableSharedFlow<MessageChange>(extraBufferCapacity = 1_000)
    var openedChats = 0
    var closedChats = 0
    private var nextMessageId = 2_000_000L
    var onFetch: suspend (fromMessageId: Long) -> Unit = {}
    private val stateFiles = mutableListOf<File>()
    private var folderStateDocument: RemoteDocument? = null
    private var folderStateFile: File? = null

    fun publishFolderState(state: RemoteFolderState, messageId: Long = 1_000_000) {
        val file = File.createTempFile("folders", ".json").also { stateFiles += it }
        file.writeText(Json.encodeToString(RemoteFolderState.serializer(), state))
        folderStateFile = file
        folderStateDocument = RemoteDocument(
            chatId = TEST_CHAT,
            messageId = messageId,
            remoteFileId = "folder-state",
            uniqueFileId = "folder-state-$messageId",
            fileName = RemoteFolderState.FILE_NAME,
            mimeType = "application/json",
            sizeBytes = file.length(),
            caption = RemoteFolderState.MARKER,
            dateSeconds = 100
        )
    }

    fun publishRawFolderState(json: String, messageId: Long = 1_000_000) {
        publishFolderState(RemoteFolderState(), messageId)
        folderStateFile!!.writeText(json)
    }

    override suspend fun ensureStorageChat(knownChatId: Long?): Long = TEST_CHAT

    override suspend fun fetchDocuments(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): RemoteDocumentPage {
        onFetch(fromMessageId)
        val newestFirst = (documents + listOfNotNull(folderStateDocument))
            .sortedByDescending { it.messageId }
            .filter { fromMessageId == 0L || it.messageId < fromMessageId }
            .take(limit)
        return RemoteDocumentPage(
            documents = newestFirst,
            nextFromMessageId = newestFirst.lastOrNull()?.messageId ?: 0L
        )
    }

    fun uploadedFolderState(): RemoteFolderState = Json { ignoreUnknownKeys = true }
        .decodeFromString(
            RemoteFolderState.serializer(),
            uploads.last { it.first == RemoteFolderState.FILE_NAME }.second
        )

    override fun uploadDocument(
        chatId: Long,
        localPath: String,
        fileName: String,
        mimeType: String,
        caption: String,
        thumbnailPath: String?
    ): Flow<TelegramUploadEvent> {
        val content = File(localPath).readText()
        uploads += fileName to content
        val messageId = nextMessageId++
        if (fileName == RemoteFolderState.FILE_NAME) {
            publishRawFolderState(content, messageId)
        }
        return flowOf(
            TelegramUploadEvent.Completed(
                RemoteDocument(
                    chatId = chatId,
                    messageId = messageId,
                    remoteFileId = "uploaded-$messageId",
                    uniqueFileId = "uploaded-unique-$messageId",
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = content.length.toLong(),
                    caption = caption,
                    dateSeconds = 100
                )
            )
        )
    }

    override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>) {
        deletedMessages += messageIds
    }

    override suspend fun getDocument(chatId: Long, messageId: Long): RemoteDocument? =
        (documents + listOfNotNull(folderStateDocument)).firstOrNull { it.messageId == messageId }

    override fun messageChanges(chatId: Long): Flow<MessageChange> = changes

    override suspend fun openChat(chatId: Long) {
        openedChats++
    }

    override suspend fun closeChat(chatId: Long) {
        closedChats++
    }

    override fun downloadDocument(remoteFileId: String): Flow<TelegramDownloadEvent> {
        if (failDownloads) return flowOf()
        val file = folderStateFile ?: return flowOf()
        return flowOf(TelegramDownloadEvent.Completed(file.absolutePath, file.length()))
    }

    override suspend fun editCaption(chatId: Long, messageId: Long, caption: String) {
        editedCaptions[messageId] = caption
    }

    fun cleanUp() {
        stateFiles.forEach { it.delete() }
    }
}
