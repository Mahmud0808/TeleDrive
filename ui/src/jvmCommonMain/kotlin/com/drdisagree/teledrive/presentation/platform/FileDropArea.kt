package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Accepts files dragged from the platform's file manager, handing importer
 * references to [onDropped]. Platforms without external drag pass through.
 */
@Composable
expect fun FileDropArea(
    onDropped: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)
