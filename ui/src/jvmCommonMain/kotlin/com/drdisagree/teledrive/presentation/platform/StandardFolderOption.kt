package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.StringResource

/** A well-known media folder the platform offers as a one-tap backup source. */
data class StandardFolderOption(
    val label: StringResource,
    val path: String
)

val LocalStandardFolders = staticCompositionLocalOf<List<StandardFolderOption>> {
    emptyList()
}
