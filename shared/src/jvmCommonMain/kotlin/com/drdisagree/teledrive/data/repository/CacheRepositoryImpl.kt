package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.StorageInspector
import com.drdisagree.teledrive.core.media.ThumbnailMemoryCache
import com.drdisagree.teledrive.data.local.dao.CacheDao
import com.drdisagree.teledrive.data.local.dao.ThumbnailDao
import com.drdisagree.teledrive.data.local.entity.CacheEntryType
import com.drdisagree.teledrive.domain.repository.CacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class CacheRepositoryImpl(
    private val storagePaths: AppStoragePaths,
    private val cacheDao: CacheDao,
    private val thumbnailDao: ThumbnailDao,
    private val storageInspector: StorageInspector,
    private val thumbnailMemoryCache: ThumbnailMemoryCache
) : CacheRepository {

    private val stats = MutableStateFlow(CacheRepository.CacheStats(0, 0, 0, 0, 0))

    override fun observeStats(): Flow<CacheRepository.CacheStats> = stats

    override suspend fun refreshStats() {
        stats.value = CacheRepository.CacheStats(
            thumbnailBytes = thumbnailDao.totalSizeBytes(),
            previewBytes = cacheDao.sizeByType(CacheEntryType.PREVIEW),
            streamBytes = cacheDao.sizeByType(CacheEntryType.STREAM),
            tempBytes = cacheDao.sizeByType(CacheEntryType.TEMP) + stagingSize() + partSize(),
            tdlibBytes = storageInspector.directorySize(tdlibFilesDir())
        )
    }

    override suspend fun clearThumbnails() {
        thumbnailDir().deleteRecursively()
        thumbnailDao.deleteAll()
        thumbnailMemoryCache.clear()
        refreshStats()
    }

    override suspend fun clearLinkThumbnails() {
        for (thumbnail in thumbnailDao.textFileThumbnails()) {
            File(thumbnail.path).delete()
            thumbnailDao.delete(thumbnail.fileId)
            thumbnailMemoryCache.remove(thumbnail.fileId)
        }
        refreshStats()
    }

    override suspend fun clearTemp() {
        clearType(CacheEntryType.TEMP)
        stagingDir().deleteRecursively()
        assemblyDir().deleteRecursively()
        refreshStats()
    }

    override suspend fun clearAll() {
        clearThumbnails()
        clearType(CacheEntryType.PREVIEW)
        clearType(CacheEntryType.STREAM)
        clearTemp()
        tdlibFilesDir().deleteRecursively()
        refreshStats()
    }

    override suspend fun enforceLimit(maxBytes: Long) {
        refreshStats()
        var total = stats.value.totalBytes
        if (total <= maxBytes) return

        while (total > maxBytes) {
            val victims = cacheDao.leastRecentlyUsed(32)
            if (victims.isEmpty()) break
            for (victim in victims) {
                File(victim.path).delete()
                cacheDao.delete(victim.path)
                total -= victim.sizeBytes
                if (total <= maxBytes) break
            }
        }

        while (total > maxBytes) {
            val victims = thumbnailDao.leastRecentlyUsed(64)
            if (victims.isEmpty()) break
            for (victim in victims) {
                File(victim.path).delete()
                thumbnailDao.delete(victim.fileId)
                total -= victim.sizeBytes
                if (total <= maxBytes) break
            }
        }
        refreshStats()
    }

    private suspend fun clearType(type: CacheEntryType) {
        for (entry in cacheDao.byType(type)) {
            File(entry.path).delete()
            cacheDao.delete(entry.path)
        }
    }

    private fun thumbnailDir(): File = File(storagePaths.cacheDir, "thumbnails")

    private fun stagingDir(): File = File(storagePaths.cacheDir, "staging")

    private fun stagingSize(): Long = storageInspector.directorySize(stagingDir())

    private fun partsDir(): File = File(storagePaths.cacheDir, "parts")

    private fun assemblyDir(): File = File(storagePaths.cacheDir, "assembly")

    private fun partSize(): Long =
        storageInspector.directorySize(partsDir()) +
                storageInspector.directorySize(assemblyDir())

    private fun tdlibFilesDir(): File = File(File(storagePaths.filesDir, "tdlib"), "files")
}
