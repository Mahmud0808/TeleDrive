package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** True on platforms where the user can choose where downloads are written. */
val LocalDownloadLocationConfigurable = staticCompositionLocalOf { false }
