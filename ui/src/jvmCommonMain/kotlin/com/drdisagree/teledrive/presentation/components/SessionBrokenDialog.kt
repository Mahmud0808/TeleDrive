package com.drdisagree.teledrive.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.window.DialogProperties
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.session_broken_action
import com.drdisagree.teledrive.resources.session_broken_message
import com.drdisagree.teledrive.resources.session_broken_title

/** No way out but signing in again, since the app cannot reach Telegram at all. */
@Composable
fun SessionBrokenDialog(onSignInAgain: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(stringResource(Res.string.session_broken_title)) },
        text = { Text(stringResource(Res.string.session_broken_message)) },
        confirmButton = {
            Button(onClick = onSignInAgain, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.session_broken_action))
            }
        }
    )
}
