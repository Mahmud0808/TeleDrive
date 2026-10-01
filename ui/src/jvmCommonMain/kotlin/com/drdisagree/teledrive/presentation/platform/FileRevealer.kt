package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Absent on platforms without a browsable file system, which hides the action. */
fun interface FileRevealer {
    fun reveal(path: String): Boolean
}

val LocalFileRevealer = staticCompositionLocalOf<FileRevealer?> { null }
