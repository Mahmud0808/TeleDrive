package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.media.MimeTypes
import com.drdisagree.teledrive.core.media.ThumbnailStore
import com.drdisagree.teledrive.core.storage.AppStoragePaths
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.data.local.entity.FileEntity
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

class ApkIconUploader(
    private val telegramClient: TelegramClient,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val streamCrypto: StreamCrypto,
    private val thumbnailStore: ThumbnailStore,
    private val storagePaths: AppStoragePaths
) {
    private fun stagingDir(): File = storagePaths.cacheDir.resolve("staging").apply { mkdirs() }

    suspend fun uploadIconIfApk(
        entity: FileEntity,
        chatId: Long,
        encrypt: Boolean
    ): String? {
        if (!MimeTypes.isApk(entity.mimeType, entity.name)) return null

        val rawIconPath = thumbnailStore.uploadThumbnailFile(entity.id)?.absolutePath ?: return null

        val iconUploadPath: String
        val iconFileName: String
        val iconMimeType: String
        var stagingIconFile: File? = null

        if (encrypt) {
            val key = runCatching { wrappedKeyRepository.getOrCreate(CryptoKeys.CONTENT) }.getOrNull()
                ?: return null
            val rawIconBytes = runCatching { File(rawIconPath).readBytes() }.getOrNull()
                ?: return null

            val encryptedBytes = streamCrypto.encryptBytes(key, rawIconBytes)
            val randomName = "${UUID.randomUUID()}.tde"
            val staging = File(stagingDir(), randomName)
            staging.writeBytes(encryptedBytes)
            stagingIconFile = staging
            iconUploadPath = staging.absolutePath
            iconFileName = randomName
            iconMimeType = "application/octet-stream"
        } else {
            iconUploadPath = rawIconPath
            iconFileName = "${UUID.randomUUID()}.jpg"
            iconMimeType = "image/jpeg"
        }

        var resultId: String? = null
        try {
            runCatching {
                telegramClient.uploadDocument(
                    chatId = chatId,
                    localPath = iconUploadPath,
                    fileName = iconFileName,
                    mimeType = iconMimeType,
                    caption = ICON_CAPTION,
                    thumbnailPath = null
                ).first { it is TelegramUploadEvent.Completed } as? TelegramUploadEvent.Completed
            }.getOrNull()?.let { completed ->
                val doc = completed.document
                resultId = "${doc.remoteFileId}:${doc.messageId}"
            }
        } finally {
            stagingIconFile?.delete()
        }

        return resultId
    }

    companion object {
        const val ICON_CAPTION = "#teledrive-icon"
    }
}
