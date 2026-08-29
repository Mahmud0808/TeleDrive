package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shows a file inside the platform's file manager. Absent on platforms
 * without a browsable file system, which hides the action entirely.
 */
fun interface FileRevealer {
    fun reveal(path: String): Boolean
}

val LocalFileRevealer = staticCompositionLocalOf<FileRevealer?> { null }
