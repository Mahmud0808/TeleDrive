package com.drdisagree.teledrive.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.presentation.navigation.LocalBottomBarInset

/**
 * The bar is drawn over the screen, so a plain host would sit behind it. The inset animates,
 * because swapping the host would rebuild the snackbar mid-flight.
 */
@Composable
fun BottomBarSnackbarHost(
    hostState: SnackbarHostState,
    applyInset: Boolean = true,
    modifier: Modifier = Modifier
) {
    val target = if (applyInset) LocalBottomBarInset.current else 0.dp
    val inset by animateDpAsState(targetValue = target, label = "snackbarInset")

    SnackbarHost(
        hostState = hostState,
        modifier = Modifier
            .padding(bottom = inset)
            .then(modifier)
    )
}
