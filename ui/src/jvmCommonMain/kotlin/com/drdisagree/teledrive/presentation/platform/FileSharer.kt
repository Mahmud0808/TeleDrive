package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

fun interface FileSharer {

    fun share(paths: List<String>, mimeType: String, chooserTitle: String)
}

val LocalFileSharer = staticCompositionLocalOf<FileSharer> {
    error("FileSharer is not provided")
}
