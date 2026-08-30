package com.drdisagree.teledrive.presentation.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Shows each message as it arrives, replacing whatever is on screen. A queue
 * would leave the newest result waiting behind stale text, so the current
 * snackbar is dismissed and the next one shown right away.
 */
@Composable
fun CollectSnackbarMessages(messages: Flow<UiText>, hostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(messages, hostState) {
        messages.collect { message ->
            val text = message.load()
            hostState.currentSnackbarData?.dismiss()
            scope.launch { hostState.showSnackbar(text) }
        }
    }
}
