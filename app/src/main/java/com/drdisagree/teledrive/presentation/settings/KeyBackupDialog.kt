package com.drdisagree.teledrive.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_cancel
import com.drdisagree.teledrive.resources.common_restore
import com.drdisagree.teledrive.resources.settings_back_encryption_key
import com.drdisagree.teledrive.resources.settings_enter_passphrase_used_backing
import com.drdisagree.teledrive.resources.settings_forgot_show_hint
import com.drdisagree.teledrive.resources.settings_hint_optional
import com.drdisagree.teledrive.resources.settings_no_key_backup_found
import com.drdisagree.teledrive.resources.settings_passphrase
import com.drdisagree.teledrive.resources.settings_passphrase_min_length
import com.drdisagree.teledrive.resources.settings_passphrases_do_not_match
import com.drdisagree.teledrive.resources.settings_reading_hint
import com.drdisagree.teledrive.resources.settings_repeat_passphrase
import com.drdisagree.teledrive.resources.settings_required_encryption_key_wrapped
import com.drdisagree.teledrive.resources.settings_restore_encryption_key
import com.drdisagree.teledrive.resources.settings_save_backup
import com.drdisagree.teledrive.resources.settings_stored_unencrypted_never_put

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyBackupDialog(
    working: Boolean,
    onConfirm: (passphrase: String, hint: String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    val mismatch = repeated.isNotEmpty() && passphrase != repeated
    val tooShort = passphrase.isNotEmpty() && passphrase.length < MIN_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_back_encryption_key)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.settings_required_encryption_key_wrapped),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(Res.string.settings_passphrase)) },
                    singleLine = true,
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        {
                            Text(
                                stringResource(
                                    Res.string.settings_passphrase_min_length,
                                    MIN_LENGTH
                                )
                            )
                        }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = { Text(stringResource(Res.string.settings_repeat_passphrase)) },
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(Res.string.settings_passphrases_do_not_match)) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text(stringResource(Res.string.settings_hint_optional)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(Res.string.settings_stored_unencrypted_never_put)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase, hint.trim()) },
                shapes = ButtonDefaults.shapes(),
                enabled = !working && passphrase.length >= MIN_LENGTH && passphrase == repeated
            ) { Text(stringResource(Res.string.settings_save_backup)) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyRestoreDialog(
    working: Boolean,
    hint: KeyHint,
    onShowHint: () -> Unit,
    onConfirm: (passphrase: String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_restore_encryption_key)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.settings_enter_passphrase_used_backing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                when (hint) {
                    is KeyHint.Unknown -> TextButton(
                        onClick = onShowHint,
                        contentPadding = PaddingValues(0.dp)
                    ) { Text(stringResource(Res.string.settings_forgot_show_hint)) }

                    is KeyHint.Loading -> Text(
                        text = stringResource(Res.string.settings_reading_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    is KeyHint.Missing -> Text(
                        text = stringResource(Res.string.settings_no_key_backup_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    is KeyHint.Loaded -> Text(
                        text = hint.text?.let { saved -> "Hint: $saved" }
                            ?: "No hint was saved with this backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(Res.string.settings_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase) },
                shapes = ButtonDefaults.shapes(),
                enabled = passphrase.isNotEmpty() && !working
            ) { Text(stringResource(Res.string.common_restore)) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.common_cancel)) }
        }
    )
}

private const val MIN_LENGTH = 8
