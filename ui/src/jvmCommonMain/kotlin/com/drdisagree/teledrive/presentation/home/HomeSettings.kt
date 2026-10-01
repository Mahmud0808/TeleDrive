package com.drdisagree.teledrive.presentation.home

import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.domain.model.UserPreferences

internal data class HomeSettings(
    val prefs: UserPreferences,
    val syncing: Boolean,
    val activeDrive: DriveChannel?,
    val charging: Boolean
)
