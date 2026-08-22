package com.drdisagree.teledrive.data.repository

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.drdisagree.teledrive.core.files.StorageInspector
import com.drdisagree.teledrive.core.media.thumbnailCacheKey
import com.drdisagree.teledrive.data.local.dao.CacheDao
import com.drdisagree.teledrive.data.local.dao.ThumbnailDao
import com.drdisagree.teledrive.data.local.entity.CacheEntryType
import com.drdisagree.teledrive.domain.repository.CacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cacheDao: CacheDao,
    private val thumbnailDao: ThumbnailDao,
    private val storageInspector: StorageInspector,
    private val imageLoader: ImageLoader
) : CacheRepository {

    private val stats = MutableStateFlow(CacheRepository.CacheStats(0, 0, 0, 0, 0))

    override fun observeStats(): Flow<CacheRepository.CacheStats> = stats

    override suspend fun refreshStats() {
        stats.value = CacheRepository.CacheStats(
            thumbnailBytes = thumbnailDao.totalSizeBytes(),
            previewBytes = cacheDao.sizeByType(CacheEntryType.PREVIEW),
            streamBytes = cacheDao.sizeByType(CacheEntryType.STREAM),
            tempBytes = cacheDao.sizeByType(CacheEntryType.TEMP) + stagingSize(),
            tdlibBytes = storageInspector.directorySize(tdlibFilesDir())
        )
    }

    override suspend fun clearThumbnails() {
        thumbnailDir().deleteRecursively()
        thumbnailDao.deleteAll()
        imageLoader.memoryCache?.clear()
        refreshStats()
    }

    override suspend fun clearLinkThumbnails() {
        for (thumbnail in thumbnailDao.textFileThumbnails()) {
            File(thumbnail.path).delete()
            thumbnailDao.delete(thumbnail.fileId)
            imageLoader.memoryCache?.remove(MemoryCache.Key(thumbnailCacheKey(thumbnail.fileId)))
        }
        refreshStats()
    }

    override suspend fun clearTemp() {
        clearType(CacheEntryType.TEMP)
        stagingDir().deleteRecursively()
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

    private fun thumbnailDir(): File = File(context.cacheDir, "thumbnails")

    private fun stagingDir(): File = File(context.cacheDir, "staging")

    private fun stagingSize(): Long = storageInspector.directorySize(stagingDir())

    private fun tdlibFilesDir(): File = File(File(context.filesDir, "tdlib"), "files")
}
