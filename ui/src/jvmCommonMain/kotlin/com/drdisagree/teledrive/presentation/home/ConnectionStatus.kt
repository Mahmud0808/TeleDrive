package com.drdisagree.teledrive.presentation.home

import com.drdisagree.teledrive.presentation.components.ConnectionIndicator
import org.jetbrains.compose.resources.StringResource

internal data class ConnectionStatus(
    val indicator: ConnectionIndicator,
    val labelRes: StringResource
)
