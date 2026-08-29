package com.drdisagree.teledrive.core.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import kotlinx.coroutines.flow.first

class TrashCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
    private val trashRepository: TrashRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val days = settingsRepository.preferences.first().trashAutoClearDays
        if (days <= 0) return Result.success()
        return when (trashRepository.clearExpired(days)) {
            is AppResult.Success -> Result.success()
            is AppResult.Failure -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "trash_cleanup"
    }
}
