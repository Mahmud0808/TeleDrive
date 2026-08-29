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
 * Snackbar host that clears the floating navigation bar. The bar is drawn over
 * the screen rather than inside its Scaffold, so a plain host would sit behind
 * it; child screens have no bar and get no extra padding.
 *
 * [applyInset] is false while an action button already lifts the snackbar,
 * since Scaffold stacks it above that button. The value animates rather than
 * switching, because swapping the host itself would tear the snackbar down and
 * rebuild it mid-flight.
 */
@Composable
fun BottomBarSnackbarHost(
    hostState: SnackbarHostState,
    applyInset: Boolean = true
) {
    val target = if (applyInset) LocalBottomBarInset.current else 0.dp
    val inset by animateDpAsState(targetValue = target, label = "snackbarInset")
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier.padding(bottom = inset)
    )
}
