package com.drdisagree.teledrive.presentation.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.home_encrypted_later
import com.drdisagree.teledrive.resources.home_encrypted_message
import com.drdisagree.teledrive.resources.home_encrypted_open_settings
import com.drdisagree.teledrive.resources.home_encrypted_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EncryptedFilesDialog(
    count: Int,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.home_encrypted_title)) },
        text = { Text(pluralStringResource(Res.plurals.home_encrypted_message, count, count)) },
        confirmButton = {
            Button(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.home_encrypted_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.home_encrypted_later))
            }
        }
    )
}
