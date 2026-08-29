package com.drdisagree.teledrive.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.app_name
import com.drdisagree.teledrive.resources.backup
import com.drdisagree.teledrive.resources.common_try_again
import com.drdisagree.teledrive.resources.onboarding_action_continue
import com.drdisagree.teledrive.resources.onboarding_allow_all_files
import com.drdisagree.teledrive.resources.onboarding_allow_media
import com.drdisagree.teledrive.resources.onboarding_api_access
import com.drdisagree.teledrive.resources.onboarding_api_hash
import com.drdisagree.teledrive.resources.onboarding_api_id
import com.drdisagree.teledrive.resources.onboarding_channel_files_in_channel
import com.drdisagree.teledrive.resources.onboarding_choose_country
import com.drdisagree.teledrive.resources.onboarding_choose_drive
import com.drdisagree.teledrive.resources.onboarding_choose_what_back_change
import com.drdisagree.teledrive.resources.onboarding_code
import com.drdisagree.teledrive.resources.onboarding_code_sent
import com.drdisagree.teledrive.resources.onboarding_code_via_call
import com.drdisagree.teledrive.resources.onboarding_code_via_email
import com.drdisagree.teledrive.resources.onboarding_code_via_flash_call
import com.drdisagree.teledrive.resources.onboarding_code_via_fragment
import com.drdisagree.teledrive.resources.onboarding_code_via_missed_call
import com.drdisagree.teledrive.resources.onboarding_code_via_sms
import com.drdisagree.teledrive.resources.onboarding_code_via_sms_phrase
import com.drdisagree.teledrive.resources.onboarding_code_via_sms_word
import com.drdisagree.teledrive.resources.onboarding_code_via_telegram
import com.drdisagree.teledrive.resources.onboarding_continue
import com.drdisagree.teledrive.resources.onboarding_continue_without
import com.drdisagree.teledrive.resources.onboarding_country
import com.drdisagree.teledrive.resources.onboarding_drive_created_desc
import com.drdisagree.teledrive.resources.onboarding_drive_ready
import com.drdisagree.teledrive.resources.onboarding_email_code_title
import com.drdisagree.teledrive.resources.onboarding_email_continue
import com.drdisagree.teledrive.resources.onboarding_email_description
import com.drdisagree.teledrive.resources.onboarding_email_label
import com.drdisagree.teledrive.resources.onboarding_email_title
import com.drdisagree.teledrive.resources.onboarding_enter_code
import com.drdisagree.teledrive.resources.onboarding_enter_password
import com.drdisagree.teledrive.resources.onboarding_files_access_covers_documents
import com.drdisagree.teledrive.resources.onboarding_files_live_own_private
import com.drdisagree.teledrive.resources.onboarding_get_api_keys
import com.drdisagree.teledrive.resources.onboarding_get_started
import com.drdisagree.teledrive.resources.onboarding_got
import com.drdisagree.teledrive.resources.onboarding_include_country_code_like
import com.drdisagree.teledrive.resources.onboarding_looking_for_drives
import com.drdisagree.teledrive.resources.onboarding_my_telegram_org
import com.drdisagree.teledrive.resources.onboarding_no_country_match
import com.drdisagree.teledrive.resources.onboarding_no_drive_yet
import com.drdisagree.teledrive.resources.onboarding_number
import com.drdisagree.teledrive.resources.onboarding_one_time_setup_keys
import com.drdisagree.teledrive.resources.onboarding_open_dev_page
import com.drdisagree.teledrive.resources.onboarding_opening
import com.drdisagree.teledrive.resources.onboarding_password
import com.drdisagree.teledrive.resources.onboarding_phone_number
import com.drdisagree.teledrive.resources.onboarding_phone_number_telegram_account
import com.drdisagree.teledrive.resources.onboarding_pick_drive_desc
import com.drdisagree.teledrive.resources.onboarding_qr_code_description
import com.drdisagree.teledrive.resources.onboarding_qr_confirm_instead
import com.drdisagree.teledrive.resources.onboarding_qr_description
import com.drdisagree.teledrive.resources.onboarding_qr_open_failed
import com.drdisagree.teledrive.resources.onboarding_qr_open_telegram
import com.drdisagree.teledrive.resources.onboarding_qr_same_device_description
import com.drdisagree.teledrive.resources.onboarding_qr_scan_instead
import com.drdisagree.teledrive.resources.onboarding_qr_sign_in
import com.drdisagree.teledrive.resources.onboarding_qr_step_1
import com.drdisagree.teledrive.resources.onboarding_qr_step_2
import com.drdisagree.teledrive.resources.onboarding_qr_step_3
import com.drdisagree.teledrive.resources.onboarding_qr_title
import com.drdisagree.teledrive.resources.onboarding_qr_use_phone
import com.drdisagree.teledrive.resources.onboarding_qr_waiting
import com.drdisagree.teledrive.resources.onboarding_resend_code
import com.drdisagree.teledrive.resources.onboarding_search_country
import com.drdisagree.teledrive.resources.onboarding_section_behavior
import com.drdisagree.teledrive.resources.onboarding_section_folders
import com.drdisagree.teledrive.resources.onboarding_send_code
import com.drdisagree.teledrive.resources.onboarding_set_up_proxy
import com.drdisagree.teledrive.resources.onboarding_sign
import com.drdisagree.teledrive.resources.onboarding_single_drive_desc
import com.drdisagree.teledrive.resources.onboarding_step
import com.drdisagree.teledrive.resources.onboarding_step_counter
import com.drdisagree.teledrive.resources.onboarding_storage_access
import com.drdisagree.teledrive.resources.onboarding_teledrive_needs_media_files
import com.drdisagree.teledrive.resources.onboarding_telegram_blocked
import com.drdisagree.teledrive.resources.onboarding_toggle_auto_backup
import com.drdisagree.teledrive.resources.onboarding_toggle_camera
import com.drdisagree.teledrive.resources.onboarding_toggle_movies
import com.drdisagree.teledrive.resources.onboarding_toggle_pictures
import com.drdisagree.teledrive.resources.onboarding_toggle_wifi_only
import com.drdisagree.teledrive.resources.onboarding_two_step_verification
import com.drdisagree.teledrive.resources.onboarding_verify
import com.drdisagree.teledrive.resources.onboarding_where_i_get_these
import com.drdisagree.teledrive.core.permissions.AppPermission
import com.drdisagree.teledrive.core.permissions.PermissionChecker
import com.drdisagree.teledrive.presentation.platform.LocalPermissionRequester
import com.drdisagree.teledrive.presentation.platform.LocalPlatformCapabilities
import com.drdisagree.teledrive.presentation.platform.LocalSystemScreens
import com.drdisagree.teledrive.presentation.platform.LocalTelegramLinkOpener
import com.drdisagree.teledrive.resources.ic_launcher_monochrome
import org.koin.compose.koinInject
import com.drdisagree.teledrive.core.telegram.CodeDeliveryChannel
import com.drdisagree.teledrive.domain.model.Country
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.presentation.common.UiText
import com.drdisagree.teledrive.presentation.common.resolve
import com.drdisagree.teledrive.presentation.components.ChannelAvatar
import com.drdisagree.teledrive.presentation.components.QrCode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    onOpenProxy: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.step == OnboardingStep.DONE) {
        onFinished()
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
        ) {
            Spacer(Modifier.height(20.dp))
            StepHeader(
                step = state.step,
                modifier = Modifier.padding(horizontal = PAGE_PADDING)
            )
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val availableHeight = maxHeight
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = availableHeight),
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                        targetState = state.step,
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            val spec = tween<IntOffset>(
                                durationMillis = STEP_SLIDE_MS,
                                easing = EmphasizedEasing
                            )
                            slideInHorizontally(spec) { width ->
                                if (forward) width else -width
                            }.togetherWith(
                                slideOutHorizontally(spec) { width ->
                                    if (forward) -width else width
                                }
                            ).using(SizeTransform(clip = true) { _, _ -> snap() })
                        },
                        label = stringResource(Res.string.onboarding_step)
                    ) { step ->
                        when (step) {
                            OnboardingStep.WELCOME -> WelcomeStep(onContinue = viewModel::start)
                            OnboardingStep.API_CREDENTIALS -> CredentialsStep(
                                working = state.working,
                                error = state.error,
                                onSubmit = viewModel::submitCredentials
                            )

                            OnboardingStep.EMAIL_ADDRESS -> EmailAddressStep(
                                working = state.working,
                                error = state.error,
                                onSubmit = viewModel::submitEmailAddress
                            )

                            OnboardingStep.EMAIL_CODE -> EmailCodeStep(
                                working = state.working,
                                error = state.error,
                                emailPattern = state.codePhoneNumber,
                                codeLength = state.codeLength,
                                onSubmit = viewModel::submitEmailCode
                            )

                            OnboardingStep.PHONE -> if (state.qrMode) {
                                QrStep(
                                    working = state.working,
                                    error = state.error,
                                    link = state.qrLink,
                                    onCancel = viewModel::cancelQrLogin
                                )
                            } else PhoneStep(
                                countries = state.countries,
                                selectedCountry = state.selectedCountry,
                                countryLoadState = state.countryLoadState,
                                onLoadCountries = viewModel::loadCountries,
                                onSelectCountry = viewModel::selectCountry,
                                onUseQrCode = viewModel::startQrLogin,
                                onOpenProxy = onOpenProxy,
                                working = state.working,
                                error = state.error,
                                registrationRequired = state.registrationRequired,
                                onSubmit = viewModel::submitPhone
                            )

                            OnboardingStep.CODE -> CodeStep(
                                working = state.working,
                                error = state.error,
                                phoneNumber = state.codePhoneNumber,
                                channel = state.codeChannel,
                                codeLength = state.codeLength,
                                onSubmit = viewModel::submitCode,
                                onResend = viewModel::resendCode
                            )

                            OnboardingStep.PASSWORD -> PasswordStep(
                                working = state.working,
                                error = state.error,
                                hint = state.passwordHint,
                                onSubmit = viewModel::submitPassword
                            )

                            OnboardingStep.PERMISSIONS -> PermissionsStep(
                                working = state.working,
                                onResolved = viewModel::onPermissionsResolved
                            )

                            OnboardingStep.CHANNEL_SELECT -> ChannelSelectStep(
                                channels = state.channels,
                                selectedChatId = state.selectedChatId,
                                justCreated = state.channelCreated,
                                working = state.working,
                                error = state.error,
                                onSelect = viewModel::selectChannel,
                                onRetry = viewModel::retryDriveSetup,
                                onContinue = viewModel::confirmChannel
                            )

                            OnboardingStep.BACKUP_SETUP -> BackupSetupStep(
                                state = state,
                                onDcim = viewModel::setBackupDcim,
                                onPictures = viewModel::setBackupPictures,
                                onMovies = viewModel::setBackupMovies,
                                onAutoBackup = viewModel::setAutoBackup,
                                onWifiOnly = viewModel::setWifiOnly,
                                onFinish = viewModel::finishSetup,
                                finishing = state.finishing
                            )

                            OnboardingStep.DONE -> Unit
                        }
                    }
                    Spacer(Modifier.height(32.dp + padding.calculateBottomPadding()))
                }
            }
        }
    }
}

/**
 * Shown only when the account already holds more than one drive, so the user
 * says which one this device opens before anything is indexed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelSelectStep(
    channels: List<DriveChannel>,
    selectedChatId: Long?,
    justCreated: Boolean,
    working: Boolean,
    error: UiText?,
    onSelect: (Long) -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    OnboardingPage(
        icon = Icons.Filled.CloudQueue,
        title = when {
            channels.isEmpty() && error != null ->
                stringResource(Res.string.onboarding_no_drive_yet)

            justCreated -> stringResource(Res.string.onboarding_drive_ready)
            else -> stringResource(Res.string.onboarding_choose_drive)
        },
        description = when {
            channels.isEmpty() && error != null -> error.resolve()

            justCreated -> stringResource(Res.string.onboarding_drive_created_desc)

            channels.size > 1 -> stringResource(Res.string.onboarding_pick_drive_desc)

            else -> stringResource(Res.string.onboarding_single_drive_desc)
        }
    ) {
        channels.forEach { channel ->
            val selected = channel.chatId == selectedChatId
            val single = channels.size == 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .selectable(
                        selected = selected,
                        enabled = !single,
                        role = Role.RadioButton,
                        onClick = { onSelect(channel.chatId) }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (single) {
                    ChannelAvatar(channel = channel, size = 40.dp)
                } else {
                    RadioButton(selected = selected, onClick = null)
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = channel.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            Res.string.onboarding_channel_files_in_channel,
                            channel.remoteFileCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (channels.isEmpty()) {
            BigButton(
                label = stringResource(
                    if (working) Res.string.onboarding_looking_for_drives
                    else Res.string.common_try_again
                ),
                onClick = onRetry,
                enabled = !working
            )
            return@OnboardingPage
        }
        BigButton(
            label = stringResource(
                if (working) Res.string.onboarding_opening else Res.string.onboarding_continue
            ),
            onClick = onContinue,
            enabled = selectedChatId != null && !working,
            loading = working
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepHeader(step: OnboardingStep, modifier: Modifier = Modifier) {
    val capabilities = LocalPlatformCapabilities.current
    val counted = OnboardingStep.counted(
        includePermissions = capabilities.requiresPermissions,
        includeBackup = capabilities.supportsAutoBackup
    )
    val stepNumber = step.displayNumber(counted)
    val totalSteps = counted.size
    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(100),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = stringResource(Res.string.onboarding_step_counter, stepNumber, totalSteps),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        LinearWavyProgressIndicator(
            progress = { stepNumber.toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Every onboarding step is laid out the same way: hero shape, title, supporting
 * line, then whatever that step needs. Only the content block differs.
 */
@Composable
private fun OnboardingPage(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_PADDING, vertical = PAGE_VERTICAL_PADDING)
    ) {
        HeroShape(icon = icon, iconPainter = iconPainter)
        Spacer(Modifier.height(24.dp))
        StepTitle(title = title, description = description)
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroShape(icon: ImageVector?, iconPainter: Painter?) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialShapes.Cookie9Sided.toShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            iconPainter != null -> Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(128.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepTitle(title: String, description: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    OnboardingPage(
        iconPainter = painterResource(Res.drawable.ic_launcher_monochrome),
        title = stringResource(Res.string.app_name),
        description = stringResource(Res.string.onboarding_files_live_own_private)
    ) {
        BigButton(label = stringResource(Res.string.onboarding_get_started), onClick = onContinue)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CredentialsStep(
    working: Boolean,
    error: UiText?,
    onSubmit: (String, String) -> Unit
) {
    var apiId by rememberSaveable { mutableStateOf("") }
    var apiHash by rememberSaveable { mutableStateOf("") }
    var showGuide by rememberSaveable { mutableStateOf(false) }

    OnboardingPage(
        icon = Icons.Filled.Key,
        title = stringResource(Res.string.onboarding_api_access),
        description = stringResource(Res.string.onboarding_one_time_setup_keys)
    ) {
        FilledTonalButton(
            onClick = { showGuide = true },
            shapes = ButtonDefaults.shapes()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.onboarding_where_i_get_these))
        }
        Spacer(Modifier.height(20.dp))
        OnboardingField(
            value = apiId,
            onChange = { apiId = it },
            label = stringResource(Res.string.onboarding_api_id),
            keyboardType = KeyboardType.Number,
            enabled = !working
        )
        Spacer(Modifier.height(12.dp))
        OnboardingField(
            value = apiHash,
            onChange = { apiHash = it },
            label = stringResource(Res.string.onboarding_api_hash),
            enabled = !working
        )
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_action_continue),
            enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
            onAction = { onSubmit(apiId, apiHash) }
        )
    }

    if (showGuide) {
        ApiGuideDialog(onDismiss = { showGuide = false })
    }
}

/**
 * The way out for anyone whose network blocks Telegram outright, offered where
 * they first stall rather than buried in a screen they cannot reach yet.
 */
@Composable
private fun BlockedNetworkCard(onOpenProxy: () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onOpenProxy)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.onboarding_telegram_blocked),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.onboarding_set_up_proxy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApiGuideDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.onboarding_get_api_keys)) },
        text = {
            Column {
                InstructionCard(number = 1) {
                    Column {
                        Text(
                            stringResource(Res.string.onboarding_open_dev_page),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = { uriHandler.openUri("https://my.telegram.org") },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(Res.string.onboarding_my_telegram_org))
                        }
                    }
                }
                InstructionCard(number = 2) {
                    Text(
                        "Choose “API development tools” and create an app. Any name works.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                InstructionCard(number = 3) {
                    Text(
                        "Copy the api_id and api_hash into TeleDrive.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(Res.string.onboarding_got)) }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstructionCard(number: Int, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.size(14.dp))
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun QrStep(
    working: Boolean,
    error: UiText?,
    link: String?,
    onCancel: () -> Unit
) {
    var openFailed by remember { mutableStateOf(false) }

    /* The QR payload is a tg: link, so a Telegram app on this same device can
       confirm the login directly. Scanning is only needed across devices. */
    val telegramLinkOpener = LocalTelegramLinkOpener.current
    val canConfirmHere = telegramLinkOpener.canOpenTelegram
    /* One route at a time: showing both at once reads like two required steps. */
    var scanning by rememberSaveable(canConfirmHere) { mutableStateOf(!canConfirmHere) }

    OnboardingPage(
        icon = Icons.Filled.QrCode2,
        title = stringResource(Res.string.onboarding_qr_title),
        description = if (scanning) {
            stringResource(Res.string.onboarding_qr_description)
        } else {
            stringResource(Res.string.onboarding_qr_same_device_description)
        }
    ) {
        if (scanning) {
            Surface(
                color = QR_SURFACE,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth(QR_WIDTH_FRACTION)
                    .align(Alignment.CenterHorizontally)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(QR_PADDING)) {
                    if (link == null) {
                        CircularProgressIndicator(modifier = Modifier.padding(QR_PADDING))
                    } else {
                        QrCode(
                            content = link,
                            contentDescription = stringResource(
                                Res.string.onboarding_qr_code_description
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            QrInstruction(1, stringResource(Res.string.onboarding_qr_step_1))
            QrInstruction(2, stringResource(Res.string.onboarding_qr_step_2))
            QrInstruction(3, stringResource(Res.string.onboarding_qr_step_3))
        } else {
            BigButton(
                label = stringResource(Res.string.onboarding_qr_open_telegram),
                enabled = !working && link != null,
                onClick = {
                    openFailed = link == null || !telegramLinkOpener.open(link)
                }
            )
            if (openFailed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.onboarding_qr_open_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (error != null) {
            Text(
                text = error.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER_SIZE),
                    strokeWidth = SPINNER_STROKE
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.onboarding_qr_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (canConfirmHere) {
            TextButton(
                onClick = { scanning = !scanning },
                enabled = !working,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    stringResource(
                        if (scanning) {
                            Res.string.onboarding_qr_confirm_instead
                        } else {
                            Res.string.onboarding_qr_scan_instead
                        }
                    )
                )
            }
        }
        TextButton(
            onClick = onCancel,
            enabled = !working,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(stringResource(Res.string.onboarding_qr_use_phone)) }
    }
}

@Composable
private fun QrInstruction(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(QR_STEP_BADGE)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhoneStep(
    working: Boolean,
    error: UiText?,
    registrationRequired: Boolean,
    countries: List<Country>,
    selectedCountry: Country?,
    countryLoadState: CountryLoadState,
    onLoadCountries: () -> Unit,
    onSelectCountry: (Country) -> Unit,
    onUseQrCode: () -> Unit,
    onOpenProxy: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var national by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var typedBeforeCountries by rememberSaveable { mutableStateOf(false) }
    val manual = countryLoadState == CountryLoadState.FAILED || typedBeforeCountries
    val callingCode = if (manual) "" else selectedCountry?.callingCode.orEmpty()

    LaunchedEffect(Unit) { onLoadCountries() }

    OnboardingPage(
        icon = Icons.Filled.PhoneAndroid,
        title = stringResource(Res.string.onboarding_number),
        description = stringResource(Res.string.onboarding_phone_number_telegram_account)
    ) {
        if (!manual) {
            CountryField(
                country = selectedCountry,
                loading = countryLoadState == CountryLoadState.LOADING,
                enabled = !working && countries.isNotEmpty(),
                onClick = { picking = true }
            )
            Spacer(Modifier.height(12.dp))
        }
        OnboardingField(
            value = national,
            onChange = { entry ->
                if (countryLoadState == CountryLoadState.LOADING) typedBeforeCountries = true
                national = entry.filter { it.isDigit() }
            },
            label = stringResource(Res.string.onboarding_phone_number),
            supportingText = if (manual) {
                stringResource(Res.string.onboarding_include_country_code_like)
            } else {
                null
            },
            keyboardType = KeyboardType.Phone,
            enabled = !working,
            prefix = "+$callingCode"
        )
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_send_code),
            enabled = national.isNotBlank() && !registrationRequired,
            onAction = { onSubmit("+$callingCode$national") }
        )
        TextButton(
            onClick = onUseQrCode,
            enabled = !working,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Filled.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(QR_BUTTON_ICON)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.onboarding_qr_sign_in))
        }
        Spacer(Modifier.height(12.dp))
        BlockedNetworkCard(onOpenProxy = onOpenProxy)
    }

    if (picking) {
        CountryPickerSheet(
            countries = countries,
            onDismiss = { picking = false },
            onSelect = { country ->
                onSelectCountry(country)
                picking = false
            }
        )
    }
}

@Composable
private fun CountryField(
    country: Country?,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box {
        OutlinedTextField(
            value = country?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(Res.string.onboarding_country)) },
            trailingIcon = {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SPINNER_SIZE),
                        strokeWidth = SPINNER_STROKE
                    )
                } else {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(OutlinedTextFieldDefaults.shape)
                .clickable(enabled = enabled, onClick = onClick)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerSheet(
    countries: List<Country>,
    onDismiss: () -> Unit,
    onSelect: (Country) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(countries, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            countries
        } else {
            countries.filter { country ->
                country.name.contains(trimmed, ignoreCase = true) ||
                        country.callingCode.contains(trimmed) ||
                        country.isoCode.equals(trimmed, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(Res.string.onboarding_choose_country),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            OnboardingField(
                value = query,
                onChange = { query = it },
                label = stringResource(Res.string.onboarding_search_country)
            )
            Spacer(Modifier.height(12.dp))
            if (matches.isEmpty()) {
                Text(
                    text = stringResource(Res.string.onboarding_no_country_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(matches, key = { "${it.isoCode}_${it.callingCode}" }) { country ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onSelect(country) }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = country.flag, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = country.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "+${country.callingCode}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


/** Explains where the code actually went, as Telegram reported it. */
@Composable
private fun codeDeliveryText(channel: CodeDeliveryChannel, target: String): String =
    when (channel) {
        CodeDeliveryChannel.TELEGRAM_APP -> stringResource(Res.string.onboarding_code_via_telegram)
        CodeDeliveryChannel.SMS -> stringResource(Res.string.onboarding_code_via_sms, target)
        CodeDeliveryChannel.SMS_WORD -> stringResource(
            Res.string.onboarding_code_via_sms_word,
            target
        )

        CodeDeliveryChannel.SMS_PHRASE ->
            stringResource(Res.string.onboarding_code_via_sms_phrase, target)

        CodeDeliveryChannel.CALL -> stringResource(Res.string.onboarding_code_via_call, target)
        CodeDeliveryChannel.FLASH_CALL ->
            stringResource(Res.string.onboarding_code_via_flash_call, target)

        CodeDeliveryChannel.MISSED_CALL ->
            stringResource(Res.string.onboarding_code_via_missed_call, target)

        CodeDeliveryChannel.FRAGMENT -> stringResource(
            Res.string.onboarding_code_via_fragment,
            target
        )

        CodeDeliveryChannel.FIREBASE -> stringResource(Res.string.onboarding_code_via_sms, target)
        CodeDeliveryChannel.EMAIL -> stringResource(Res.string.onboarding_code_via_email, target)
        CodeDeliveryChannel.OTHER -> stringResource(Res.string.onboarding_code_sent)
    }

@Composable
private fun EmailAddressStep(
    working: Boolean,
    error: UiText?,
    onSubmit: (String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    OnboardingPage(
        icon = Icons.Filled.Email,
        title = stringResource(Res.string.onboarding_email_title),
        description = stringResource(Res.string.onboarding_email_description)
    ) {
        OnboardingField(
            value = email,
            onChange = { email = it },
            label = stringResource(Res.string.onboarding_email_label),
            keyboardType = KeyboardType.Email,
            enabled = !working
        )
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_email_continue),
            enabled = email.isNotBlank(),
            onAction = { onSubmit(email) }
        )
    }
}

@Composable
private fun EmailCodeStep(
    working: Boolean,
    error: UiText?,
    emailPattern: String,
    codeLength: Int?,
    onSubmit: (String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    OnboardingPage(
        icon = Icons.Filled.Email,
        title = stringResource(Res.string.onboarding_email_code_title),
        description = codeDeliveryText(CodeDeliveryChannel.EMAIL, emailPattern)
    ) {
        OnboardingField(
            value = code,
            onChange = { input ->
                code = input.filter(Char::isDigit).let {
                    if (codeLength != null) it.take(codeLength) else it
                }
            },
            label = stringResource(Res.string.onboarding_code),
            keyboardType = KeyboardType.NumberPassword,
            enabled = !working
        )
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_verify),
            enabled = code.isNotBlank(),
            onAction = { onSubmit(code) }
        )
    }
}

@Composable
private fun CodeStep(
    working: Boolean,
    error: UiText?,
    phoneNumber: String,
    channel: CodeDeliveryChannel,
    codeLength: Int?,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    OnboardingPage(
        icon = Icons.Filled.Sms,
        title = stringResource(Res.string.onboarding_enter_code),
        description = codeDeliveryText(channel, phoneNumber)
    ) {
        OnboardingField(
            value = code,
            onChange = { input ->
                code = input.filter(Char::isDigit).let {
                    if (codeLength != null) it.take(codeLength) else it
                }
            },
            label = stringResource(Res.string.onboarding_code),
            keyboardType = KeyboardType.NumberPassword,
            enabled = !working
        )
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_verify),
            enabled = code.isNotBlank(),
            onAction = { onSubmit(code) }
        )
        TextButton(
            onClick = onResend,
            enabled = !working,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(stringResource(Res.string.onboarding_resend_code)) }
    }
}

@Composable
private fun PasswordStep(
    working: Boolean,
    error: UiText?,
    hint: String?,
    onSubmit: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    OnboardingPage(
        icon = Icons.Filled.Lock,
        title = stringResource(Res.string.onboarding_two_step_verification),
        description = hint?.let { "Enter your Telegram password. Hint: $it" }
            ?: stringResource(Res.string.onboarding_enter_password)
    ) {
        OnboardingField(
            value = password,
            onChange = { password = it },
            label = stringResource(Res.string.onboarding_password),
            keyboardType = KeyboardType.Password,
            password = true,
            enabled = !working
        )
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_sign),
            enabled = password.isNotEmpty(),
            onAction = { onSubmit(password) }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionsStep(working: Boolean, onResolved: () -> Unit) {
    var mediaRequested by rememberSaveable { mutableStateOf(false) }
    val mediaPermissions = listOf(
        AppPermission.MEDIA_IMAGES,
        AppPermission.MEDIA_VIDEO,
        AppPermission.NOTIFICATIONS
    )
    val permissionChecker = koinInject<PermissionChecker>()
    val permissionRequester = LocalPermissionRequester.current
    val systemScreens = LocalSystemScreens.current

    fun mediaAccess(): Boolean = mediaPermissions.all { permissionChecker.isGranted(it) }

    var mediaGranted by remember { mutableStateOf(mediaAccess()) }

    var allFilesGranted by remember { mutableStateOf(permissionChecker.hasAllFilesAccess()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaGranted = mediaAccess()
                allFilesGranted = permissionChecker.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    OnboardingPage(
        icon = Icons.Filled.Photo,
        title = stringResource(Res.string.onboarding_storage_access),
        description = stringResource(Res.string.onboarding_teledrive_needs_media_files)
    ) {
        val askForMedia = !mediaGranted && !mediaRequested
        BigButton(
            label = when {
                working -> stringResource(Res.string.onboarding_looking_for_drives)
                askForMedia -> stringResource(Res.string.onboarding_allow_media)
                !allFilesGranted -> stringResource(Res.string.onboarding_allow_all_files)
                else -> stringResource(Res.string.onboarding_continue)
            },
            enabled = !working,
            loading = working,
            onClick = {
                when {
                    askForMedia -> permissionRequester.request(mediaPermissions) { mediaRequested = true }
                    !allFilesGranted -> systemScreens.openAllFilesAccess()
                    else -> onResolved()
                }
            }
        )
        if ((mediaRequested || mediaGranted) && !allFilesGranted) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_files_access_covers_documents),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onResolved,
                shapes = ButtonDefaults.shapes(),
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(Res.string.onboarding_continue_without)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupSetupStep(
    state: OnboardingUiState,
    onDcim: (Boolean) -> Unit,
    onPictures: (Boolean) -> Unit,
    onMovies: (Boolean) -> Unit,
    onAutoBackup: (Boolean) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onFinish: () -> Unit,
    finishing: Boolean
) {
    OnboardingPage(
        icon = Icons.Filled.CloudDone,
        title = stringResource(Res.string.backup),
        description = stringResource(Res.string.onboarding_choose_what_back_change)
    ) {
        SectionLabel(stringResource(Res.string.onboarding_section_folders))
        ToggleCard(stringResource(Res.string.onboarding_toggle_camera), state.backupDcim, onDcim)
        ToggleCard(
            stringResource(Res.string.onboarding_toggle_pictures),
            state.backupPictures,
            onPictures
        )
        ToggleCard(stringResource(Res.string.onboarding_toggle_movies), state.backupMovies, onMovies)
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(Res.string.onboarding_section_behavior))
        ToggleCard(
            stringResource(Res.string.onboarding_toggle_auto_backup),
            state.autoBackupEnabled,
            onAutoBackup
        )
        ToggleCard(stringResource(Res.string.onboarding_toggle_wifi_only), state.wifiOnly, onWifiOnly)
        Spacer(Modifier.height(32.dp))
        BigButton(
            label = if (finishing) "Syncing your drive…" else "Finish setup",
            onClick = onFinish,
            enabled = !finishing,
            loading = finishing
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun ToggleCard(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun OnboardingField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    enabled: Boolean = true,
    prefix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(label) },
        prefix = prefix?.let {
            {
                Text(text = it, modifier = Modifier.padding(end = PREFIX_GAP))
            }
        },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BigButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (loading) {
            LoadingIndicator(
                color = LocalContentColor.current,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErrorAndAction(
    working: Boolean,
    error: UiText?,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Column {
        error?.let {
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        BigButton(
            label = actionLabel,
            onClick = onAction,
            enabled = enabled && !working,
            loading = working
        )
    }
}

private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val STEP_SLIDE_MS = 420

private val PAGE_PADDING = 28.dp
private val PAGE_VERTICAL_PADDING = 24.dp

private val PREFIX_GAP = 4.dp
private val SPINNER_SIZE = 20.dp
private val SPINNER_STROKE = 2.dp

/* White frame behind the code: scanners expect dark modules on light. */
private val QR_SURFACE = Color.White
private const val QR_WIDTH_FRACTION = 0.8f
private val QR_PADDING = 16.dp
private val QR_STEP_BADGE = 28.dp
private val QR_BUTTON_ICON = 18.dp
private const val TELEGRAM_PROBE_URI = "tg://login"
