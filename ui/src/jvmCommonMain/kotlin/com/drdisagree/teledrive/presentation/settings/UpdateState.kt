package com.drdisagree.teledrive.presentation.settings

import com.drdisagree.teledrive.core.update.AppRelease

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: AppRelease) : UpdateState
}
