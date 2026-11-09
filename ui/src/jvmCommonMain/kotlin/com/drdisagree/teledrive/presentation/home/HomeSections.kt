package com.drdisagree.teledrive.presentation.home

import com.drdisagree.teledrive.domain.model.UserPreferences

data class HomeSections(
    val showRecent: Boolean,
    val showArchived: Boolean,
    val showHidden: Boolean
) {
    companion object {
        fun of(preferences: UserPreferences) = HomeSections(
            showRecent = preferences.showRecentFiles,
            showArchived = preferences.showArchivedFiles,
            showHidden = preferences.showHiddenFiles
        )
    }
}
