package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.presentation.platform.LocalPlatformCapabilities
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_refresh
import org.jetbrains.compose.resources.stringResource

/** Stands in for the pull gesture on platforms that do not have one. */
@Composable
fun RefreshAction(refreshing: Boolean, onRefresh: () -> Unit) {
    if (LocalPlatformCapabilities.current.supportsPullToRefresh) return
    IconButton(onClick = onRefresh, enabled = !refreshing) {
        if (refreshing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(Res.string.common_refresh)
            )
        }
    }
}
