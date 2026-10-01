package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.StringResource

data class StandardFolderOption(
    val label: StringResource,
    val path: String
)

val LocalStandardFolders = staticCompositionLocalOf<List<StandardFolderOption>> {
    emptyList()
}
