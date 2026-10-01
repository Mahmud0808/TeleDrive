package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest

internal const val TEST_CHAT = 77L

internal fun manifest(
    id: String,
    name: String,
    size: Long = 4_000_000,
    folderPath: String = "",
    folderId: String? = null,
    modifiedAt: Long = 2_000
) = RemoteFileManifest(
    fileId = id,
    name = name,
    folderPath = folderPath,
    folderId = folderId,
    mimeType = "application/octet-stream",
    sizeBytes = size,
    createdAt = 1_000,
    modifiedAt = modifiedAt
)

internal fun document(messageId: Long, caption: String, size: Long = 4_000_000) = RemoteDocument(
    chatId = TEST_CHAT,
    messageId = messageId,
    remoteFileId = "remote-$messageId",
    uniqueFileId = "unique-$messageId",
    fileName = "file-$messageId",
    mimeType = "application/octet-stream",
    sizeBytes = size,
    caption = caption,
    dateSeconds = 100
)
