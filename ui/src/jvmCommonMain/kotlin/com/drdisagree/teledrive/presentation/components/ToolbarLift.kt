package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/** True once the attached list has scrolled away from its start. */
@Composable
fun rememberToolbarLift(scrollState: ScrollableState): State<Boolean> = remember(scrollState) {
    derivedStateOf { scrollState.canScrollBackward }
}

/** Toolbar colors that pick up a container tint once content scrolls under them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun liftedTopAppBarColors(lifted: Boolean): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = if (lifted) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
)
