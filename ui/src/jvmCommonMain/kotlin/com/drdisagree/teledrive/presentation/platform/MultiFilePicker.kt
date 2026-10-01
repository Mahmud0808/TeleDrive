package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

fun interface MultiFilePicker {

    fun pick(onPicked: (List<String>) -> Unit)
}

val LocalMultiFilePicker = staticCompositionLocalOf<MultiFilePicker> {
    error("MultiFilePicker is not provided")
}
