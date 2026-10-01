package com.drdisagree.teledrive.presentation.settings

import com.drdisagree.teledrive.core.telegram.TelegramConnectionState
import com.drdisagree.teledrive.core.telegram.TelegramUser
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.domain.repository.CacheStats

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val user: TelegramUser? = null,
    val connection: TelegramConnectionState = TelegramConnectionState.CONNECTING,
    val cacheStats: CacheStats = CacheStats(0, 0, 0, 0, 0),
    val syncing: Boolean = false,
    val loading: Boolean = true
)
