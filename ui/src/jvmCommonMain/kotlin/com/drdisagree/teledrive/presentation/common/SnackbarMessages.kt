package com.drdisagree.teledrive.presentation.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Replaces whatever is on screen: a queue would leave the newest result waiting behind stale text.
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
