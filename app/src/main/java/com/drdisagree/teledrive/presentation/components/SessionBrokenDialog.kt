package com.drdisagree.teledrive.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.drdisagree.teledrive.R

/**
 * Shown when the stored session cannot be read, which leaves the app unable to
 * reach Telegram at all. There is nothing to go back to, so the dialog has no
 * way out other than signing in again.
 */
@Composable
fun SessionBrokenDialog(onSignInAgain: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(stringResource(R.string.session_broken_title)) },
        text = { Text(stringResource(R.string.session_broken_message)) },
        confirmButton = {
            Button(onClick = onSignInAgain, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.session_broken_action))
            }
        }
    )
}
