package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The storage channel every query and every new row belongs to right now. */
@Singleton
class ActiveChannel @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    suspend fun id(): Long? = settingsRepository.preferences.first().storageChatId

    fun observe(): Flow<Long?> = settingsRepository.preferences
        .map { it.storageChatId }
        .distinctUntilChanged()
}
