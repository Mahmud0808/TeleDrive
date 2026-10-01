package com.drdisagree.teledrive.core.security

import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Owns only the locked state; biometric verification happens in the UI layer. */
class AppLockManager(
    private val settingsRepository: SettingsRepository
) {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var initialized = false

    suspend fun onAppStarted() {
        val prefs = settingsRepository.preferences.first()
        if (!prefs.appLockEnabled) {
            _locked.value = false
            return
        }
        if (!initialized) {
            initialized = true
            _locked.value = true
            return
        }
        val elapsedMinutes = backgroundedAt?.let {
            (System.nanoTime() / 1_000_000 - it) / 60_000
        }
        if (elapsedMinutes != null && elapsedMinutes >= prefs.autoLockTimeoutMinutes) {
            _locked.value = true
        }
    }

    fun onAppStopped() {
        backgroundedAt = System.nanoTime() / 1_000_000
    }

    fun unlock() {
        _locked.value = false
        backgroundedAt = null
    }

    fun lockNow() {
        _locked.value = true
    }
}
