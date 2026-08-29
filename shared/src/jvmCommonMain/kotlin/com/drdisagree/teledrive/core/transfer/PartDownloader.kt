package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.KeyUnavailableException
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.data.local.dao.FilePartDao
import com.drdisagree.teledrive.data.local.entity.FilePartEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileOutputStream
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Rebuilds a split file. Parts are fetched in order and appended to one file,
 * decrypting each as it arrives, so the result is the file that was uploaded.
 *
 * The partly built file is kept between attempts and its length says how much
 * survived, which is what lets a paused or failed download carry on from the
 * part it reached instead of starting over.
 */
class PartDownloader(
    private val storagePaths: AppStoragePaths,
    private val telegramClient: TelegramClient,
    private val filePartDao: FilePartDao,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val dispatchers: DispatcherProvider
) {

    sealed interface Event {
        data class Progress(val transferredBytes: Long) : Event
        data class Joining(val partIndex: Int) : Event
        data class Completed(val localPath: String) : Event
    }

    fun download(fileId: String, encrypted: Boolean): Flow<Event> = flow {
        val parts = filePartDao.partsOf(fileId)
        if (parts.isEmpty()) error("File has no parts on record")

        val target = File(assemblyDir(), fileId)
        val resumeFrom = withContext(dispatchers.io) { resumePoint(target, parts) }
        if (resumeFrom.first == 0) target.delete()

        var written = resumeFrom.second
        emit(Event.Progress(written))

        for (part in parts.drop(resumeFrom.first)) {
            val remoteFileId = part.remoteFileId ?: error("Part ${part.partIndex + 1} is missing")
            var downloaded: String? = null
            telegramClient.downloadDocument(remoteFileId).collect { event ->
                when (event) {
                    is TelegramDownloadEvent.Progress ->
                        emit(Event.Progress(written + event.transferredBytes.coerceAtMost(part.plainSize)))

                    is TelegramDownloadEvent.Completed -> downloaded = event.localPath
                }
            }

            val source = downloaded?.let(::File)?.takeIf { it.exists() }
                ?: error("Part ${part.partIndex + 1} did not download")

            emit(Event.Joining(part.partIndex))
            withContext(dispatchers.io) { appendPart(source, target, encrypted) }

            written += part.plainSize
            emit(Event.Progress(written))
        }

        emit(Event.Completed(target.absolutePath))
    }

    suspend fun discardAssembly(fileId: String) {
        withContext(dispatchers.io) { File(assemblyDir(), fileId).delete() }
    }

    private fun appendPart(source: File, target: File, encrypted: Boolean) {
        FileOutputStream(target, true).buffered().use { output ->
            if (encrypted) {
                val key = wrappedKeyRepository.get(CryptoKeys.CONTENT)
                    ?: throw KeyUnavailableException(CryptoKeys.CONTENT)
                source.inputStream().buffered().use { input ->
                    streamCrypto.decryptStream(key, input, output)
                }
            } else {
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            }
        }
    }

    /**
     * How much of the file is already assembled. Only whole parts count: a
     * half-written one cannot be trusted, so it is dropped and fetched again.
     */
    private fun resumePoint(target: File, parts: List<FilePartEntity>): Pair<Int, Long> {
        if (!target.exists()) return 0 to 0L
        val length = target.length()
        var covered = 0L
        var index = 0
        for (part in parts) {
            if (covered + part.plainSize > length) break
            covered += part.plainSize
            index++
        }
        if (covered != length) {
            FileOutputStream(target, true).channel.use { it.truncate(covered) }
        }
        return index to covered
    }

    private fun assemblyDir(): File = File(storagePaths.cacheDir, ASSEMBLY_DIR).apply { mkdirs() }

    private companion object {
        const val ASSEMBLY_DIR = "assembly"
    }
}
