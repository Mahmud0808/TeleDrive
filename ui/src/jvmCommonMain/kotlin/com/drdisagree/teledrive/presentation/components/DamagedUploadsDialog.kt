package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_dismiss
import com.drdisagree.teledrive.resources.upload_damaged_message_many
import com.drdisagree.teledrive.resources.upload_damaged_message_one
import com.drdisagree.teledrive.resources.upload_damaged_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun DamagedUploadsDialog(fileNames: List<String>, onDismiss: () -> Unit) {
    val single = fileNames.distinct().singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.upload_damaged_title)) },
        text = {
            if (single != null) {
                Text(stringResource(Res.string.upload_damaged_message_one, single))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.upload_damaged_message_many))
                    Text(
                        fileNames.distinct().joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.common_dismiss))
            }
        }
    )
}
