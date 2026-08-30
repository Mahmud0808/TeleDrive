package com.drdisagree.teledrive.core.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drdisagree.teledrive.domain.repository.CacheRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class CacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
    private val cacheRepository: CacheRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val maxBytes = settingsRepository.preferences.first().maxCacheSizeMb.toLong() * 1024 * 1024
        cacheRepository.enforceLimit(maxBytes)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "cache_cleanup"
    }
}
