package com.drdisagree.teledrive.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
actual fun platformConfiguration(): Any? = LocalConfiguration.current
