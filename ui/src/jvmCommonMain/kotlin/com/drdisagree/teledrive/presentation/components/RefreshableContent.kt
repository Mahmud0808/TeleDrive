package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.drdisagree.teledrive.presentation.platform.LocalPlatformCapabilities

/**
 * Pull to refresh where the platform has touch, a plain container elsewhere:
 * the drag gesture fights mouse wheel scrolling on desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (LocalPlatformCapabilities.current.supportsPullToRefresh) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier
        ) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}
