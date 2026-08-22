package com.drdisagree.teledrive.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.permissions.AppPermission
import com.drdisagree.teledrive.core.permissions.PermissionChecker
import com.drdisagree.teledrive.core.permissions.openAllFilesAccess

/**
 * Lists every permission with its current state. Tapping a denied entry asks
 * again; once the system stops showing the dialog it falls back to the app's
 * settings page, which is the only way back from a permanent denial.
 */
@Composable
fun PermissionsSection(permissionChecker: PermissionChecker) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var statuses by remember { mutableStateOf(permissionChecker.statuses()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                statuses = permissionChecker.statuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { statuses = permissionChecker.statuses() }

    SettingsGroup {
        AppPermission.entries.forEach { permission ->
            val granted = statuses[permission] == true
            add(visible = permission.manifestPermission != null || permission.isSpecialAccess) {
                PermissionRow(
                    permission = permission,
                    granted = granted,
                    onClick = {
                        when {
                            granted -> openAppSettings(context)
                            permission.isSpecialAccess -> openAllFilesAccess(context)
                            else -> permission.manifestPermission?.let {
                                requestLauncher.launch(arrayOf(it))
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: AppPermission,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(permission.titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (granted) {
                    stringResource(R.string.permissions_allowed)
                } else if (permission.critical) {
                    stringResource(
                        R.string.permission_not_allowed,
                        stringResource(permission.rationaleRes)
                    )
                } else {
                    "Not allowed · optional"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (granted || !permission.critical) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
            contentDescription = if (granted) stringResource(R.string.permissions_allowed) else stringResource(
                R.string.permissions_not_allowed
            ),
            modifier = Modifier.size(22.dp),
            tint = when {
                granted -> MaterialTheme.colorScheme.primary
                permission.critical -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
        )
    }
}

private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }
}
