package com.drdisagree.teledrive.presentation.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.presentation.platform.LocalDeviceOwnerGate
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.lock_prompt_title
import com.drdisagree.teledrive.resources.lock_screen_title
import com.drdisagree.teledrive.resources.lock_unlock

/**
 * Fullscreen gate shown while the app is locked. Launches the biometric
 * prompt immediately and again on demand. Falls back to device credentials
 * when biometrics are unavailable or locked out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val deviceOwnerGate = LocalDeviceOwnerGate.current
    var statusText by remember { mutableStateOf<String?>(null) }
    var promptTrigger by remember { mutableIntStateOf(0) }
    val promptTitle = stringResource(Res.string.lock_prompt_title)

    LaunchedEffect(promptTrigger) {
        deviceOwnerGate.require(
            title = promptTitle,
            subtitle = "",
            onDenied = { error -> statusText = error }
        ) {
            onUnlocked()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.lock_screen_title),
                style = MaterialTheme.typography.titleLarge
            )
            statusText?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { promptTrigger++ },
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.lock_unlock))
            }
        }
    }
}
