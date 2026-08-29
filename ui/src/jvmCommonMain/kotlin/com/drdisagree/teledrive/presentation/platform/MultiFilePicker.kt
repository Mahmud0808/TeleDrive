package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the platform file picker for several files at once, returning importer
 * references: platform URIs on Android, plain paths on desktop.
 */
fun interface MultiFilePicker {

    fun pick(onPicked: (List<String>) -> Unit)
}

val LocalMultiFilePicker = staticCompositionLocalOf<MultiFilePicker> {
    error("MultiFilePicker is not provided")
}
