package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Opens the platform folder picker. */
fun interface FolderPicker {

    fun pick(onPicked: (PickResult) -> Unit)
}

val LocalFolderPicker = staticCompositionLocalOf<FolderPicker> {
    error("FolderPicker is not provided")
}
