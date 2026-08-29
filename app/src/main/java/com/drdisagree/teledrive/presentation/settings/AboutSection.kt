package com.drdisagree.teledrive.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.drdisagree.teledrive.BuildConfig
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.about_auto_check
import com.drdisagree.teledrive.resources.about_auto_check_summary
import com.drdisagree.teledrive.resources.about_check_updates
import com.drdisagree.teledrive.resources.about_check_updates_summary
import com.drdisagree.teledrive.resources.about_checking
import com.drdisagree.teledrive.resources.about_donate
import com.drdisagree.teledrive.resources.about_donate_summary
import com.drdisagree.teledrive.resources.about_section_project
import com.drdisagree.teledrive.resources.about_section_updates
import com.drdisagree.teledrive.resources.about_source_code
import com.drdisagree.teledrive.resources.about_source_code_summary
import com.drdisagree.teledrive.resources.about_tagline
import com.drdisagree.teledrive.resources.about_telegram
import com.drdisagree.teledrive.resources.about_telegram_summary
import com.drdisagree.teledrive.resources.about_update_ready
import com.drdisagree.teledrive.resources.about_version
import com.drdisagree.teledrive.resources.app_name
import com.drdisagree.teledrive.core.update.AppLinks
import com.drdisagree.teledrive.presentation.common.openLink
import com.drdisagree.teledrive.presentation.components.UpdateDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val checking = updateState is UpdateState.Checking

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = null,
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.extraLarge)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(Res.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(Res.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    SettingsSectionTitle(stringResource(Res.string.about_section_updates))
    SettingsGroup {
        add {
            SettingsClickRow(
                title = stringResource(Res.string.about_check_updates),
                icon = Icons.Filled.Update,
                subtitle = when (val current = updateState) {
                    is UpdateState.Available -> stringResource(
                        Res.string.about_update_ready,
                        current.release.version
                    )

                    UpdateState.Checking -> stringResource(Res.string.about_checking)
                    UpdateState.Idle -> stringResource(Res.string.about_check_updates_summary)
                },
                onClick = { if (!checking) viewModel.checkForUpdates() },
                trailing = if (checking) {
                    { LoadingIndicator(modifier = Modifier.size(24.dp)) }
                } else null
            )
        }
        add {
            SettingsSwitchRow(
                title = stringResource(Res.string.about_auto_check),
                icon = Icons.Filled.Autorenew,
                subtitle = stringResource(Res.string.about_auto_check_summary),
                checked = state.preferences.updateCheckEnabled,
                onChange = { value ->
                    viewModel.update { it.copy(updateCheckEnabled = value) }
                }
            )
        }
    }

    SettingsSectionTitle(stringResource(Res.string.about_section_project))
    SettingsGroup {
        add {
            SettingsClickRow(
                title = stringResource(Res.string.about_source_code),
                icon = Icons.Filled.Code,
                subtitle = stringResource(Res.string.about_source_code_summary),
                onClick = { openLink(context, AppLinks.REPOSITORY) }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.about_telegram),
                icon = Icons.AutoMirrored.Filled.Send,
                subtitle = stringResource(Res.string.about_telegram_summary),
                onClick = { openLink(context, AppLinks.TELEGRAM) }
            )
        }
        add {
            SettingsClickRow(
                title = stringResource(Res.string.about_donate),
                icon = Icons.Filled.Coffee,
                subtitle = stringResource(Res.string.about_donate_summary),
                onClick = { openLink(context, AppLinks.DONATE) }
            )
        }
    }

    (updateState as? UpdateState.Available)?.let { available ->
        UpdateDialog(
            release = available.release,
            onDownload = {
                viewModel.dismissUpdate()
                openLink(context, available.release.pageUrl)
            },
            onDismiss = viewModel::dismissUpdate
        )
    }
}
