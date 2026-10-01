package com.drdisagree.teledrive.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun FileDropArea(
    onDropped: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)
