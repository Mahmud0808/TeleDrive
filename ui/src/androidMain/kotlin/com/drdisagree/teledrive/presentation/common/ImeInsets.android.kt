package com.drdisagree.teledrive.presentation.common

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun imeTargetBottomInset(): Int =
    WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current)
