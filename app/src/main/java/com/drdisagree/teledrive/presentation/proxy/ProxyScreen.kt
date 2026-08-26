package com.drdisagree.teledrive.presentation.proxy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.telegram.ProxyLink
import com.drdisagree.teledrive.core.telegram.TelegramProxyType
import com.drdisagree.teledrive.domain.model.ProxyServer
import com.drdisagree.teledrive.presentation.common.CollectSnackbarMessages
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    onBack: () -> Unit,
    viewModel: ProxyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ProxyServer?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProxyServer?>(null) }

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = liftedTopAppBarColors(lifted),
                title = { Text(stringResource(R.string.proxy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { importOpen = true }) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = stringResource(R.string.proxy_paste_link)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = null
                    editorOpen = true
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.proxy_add))
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding.add(top = 8.dp, bottom = 96.dp)
        ) {
            item(key = ROUTING_CARD_KEY) {
                RoutingCard(
                    enabled = state.enabled,
                    hasProxies = state.proxies.isNotEmpty(),
                    onChange = viewModel::setEnabled,
                    modifier = Modifier.animateItem()
                )
            }
            if (!state.loading && !state.testable) {
                item(key = TESTING_NOTE_KEY) {
                    Text(
                        text = stringResource(R.string.proxy_test_needs_keys),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    )
                }
            }
            if (state.loading) {
                return@LazyColumn
            }
            if (state.proxies.isEmpty()) {
                item(key = EMPTY_STATE_KEY) {
                    EmptyState(
                        icon = Icons.Outlined.VpnKey,
                        title = stringResource(R.string.proxy_none_saved),
                        description = stringResource(R.string.proxy_none_saved_summary),
                        modifier = Modifier
                            .animateItem()
                            .padding(top = 24.dp)
                    )
                }
            } else {
                item(key = LIST_HEADER_KEY) {
                    Text(
                        text = stringResource(R.string.proxy_saved),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .animateItem()
                            .padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
                    )
                }
                items(state.proxies, key = { it.id }) { proxy ->
                    ProxyCard(
                        proxy = proxy,
                        routing = state.enabled,
                        reachability = state.reachability[proxy.id],
                        testable = state.testable,
                        modifier = Modifier.animateItem(),
                        onSelect = { viewModel.select(proxy.id) },
                        onTest = { viewModel.test(proxy) },
                        onEdit = {
                            editing = proxy
                            editorOpen = true
                        },
                        onDelete = { pendingDelete = proxy }
                    )
                }
            }
        }
    }

    if (editorOpen) {
        ProxyEditorSheet(
            original = editing,
            onSave = {
                editorOpen = false
                viewModel.save(it)
            },
            onDismiss = { editorOpen = false }
        )
    }

    if (importOpen) {
        ImportLinkSheet(
            onImport = {
                importOpen = false
                viewModel.importLink(it)
            },
            onDismiss = { importOpen = false }
        )
    }

    pendingDelete?.let { proxy ->
        ConfirmDialog(
            title = stringResource(R.string.proxy_delete_title),
            message = stringResource(R.string.proxy_delete_message, proxy.label),
            confirmLabel = stringResource(R.string.proxy_delete_action),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                viewModel.delete(proxy.id)
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/**
 * The one switch that decides whether Telegram is reached directly or through
 * the chosen route, kept above the list because it outranks every row below it.
 */
@Composable
private fun RoutingCard(
    enabled: Boolean,
    hasProxies: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ColorSpec,
        label = COLOR_LABEL
    )
    val onContainer by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ColorSpec,
        label = COLOR_LABEL
    )
    val onContainerVariant by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ColorSpec,
        label = COLOR_LABEL
    )
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = shape,
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .clickable { onChange(!enabled) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.proxy_route_through),
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                Spacer(Modifier.height(4.dp))
                val summary = when {
                    !hasProxies -> R.string.proxy_route_needs_one
                    enabled -> R.string.proxy_route_on_summary
                    else -> R.string.proxy_route_off_summary
                }
                AnimatedContent(targetState = summary, label = SUMMARY_LABEL) { line ->
                    Text(
                        text = stringResource(line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainerVariant
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProxyCard(
    proxy: ProxyServer,
    routing: Boolean,
    reachability: ProxyReachability?,
    testable: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val inUse = proxy.isActive && routing

    val container by animateColorAsState(
        targetValue = if (inUse) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = ColorSpec,
        label = COLOR_LABEL
    )
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = shape,
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = proxy.isActive, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = proxy.label.ifBlank { proxy.host },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.proxy_endpoint,
                        typeLabel(proxy.type),
                        proxy.host,
                        proxy.port
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedContent(
                    targetState = reachability,
                    label = REACHABILITY_LABEL
                ) { state ->
                    if (state == null) Box(Modifier.fillMaxWidth()) else ReachabilityLine(state)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.common_actions)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.proxy_test)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.NetworkCheck,
                                contentDescription = null
                            )
                        },
                        enabled = testable,
                        onClick = {
                            menuOpen = false
                            onTest()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.proxy_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.proxy_delete_action)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReachabilityLine(reachability: ProxyReachability) {
    val tint: Color = when (reachability) {
        ProxyReachability.TESTING -> MaterialTheme.colorScheme.onSurfaceVariant
        ProxyReachability.REACHABLE -> MaterialTheme.colorScheme.primary
        ProxyReachability.UNREACHABLE -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (reachability) {
            ProxyReachability.TESTING -> LoadingIndicator(modifier = Modifier.size(16.dp))
            ProxyReachability.REACHABLE -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )

            ProxyReachability.UNREACHABLE -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(
                when (reachability) {
                    ProxyReachability.TESTING -> R.string.proxy_testing
                    ProxyReachability.REACHABLE -> R.string.proxy_reachable
                    ProxyReachability.UNREACHABLE -> R.string.proxy_unreachable
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProxyEditorSheet(
    original: ProxyServer?,
    onSave: (ProxyServer) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(original?.type ?: TelegramProxyType.SOCKS5) }
    var label by remember { mutableStateOf(original?.label.orEmpty()) }
    var host by remember { mutableStateOf(original?.host.orEmpty()) }
    var port by remember { mutableStateOf(original?.port?.toString().orEmpty()) }
    var username by remember { mutableStateOf(original?.username.orEmpty()) }
    var password by remember { mutableStateOf(original?.password.orEmpty()) }
    var secret by remember { mutableStateOf(original?.secret.orEmpty()) }

    val portNumber = port.toIntOrNull()
    val valid = host.isNotBlank() &&
            portNumber != null &&
            portNumber in ProxyLink.MIN_PORT..ProxyLink.MAX_PORT &&
            (type != TelegramProxyType.MTPROTO || secret.isNotBlank())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = formSheetState(),
        contentWindowInsets = { SheetTopInset }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = sheetBottomPadding() + 24.dp)
        ) {
            Text(
                text = stringResource(
                    if (original == null) R.string.proxy_add else R.string.proxy_edit
                ),
                style = MaterialTheme.typography.headlineSmallEmphasized
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    ButtonGroupDefaults.ConnectedSpaceBetween
                )
            ) {
                val types = TelegramProxyType.entries
                types.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = type == option,
                        onCheckedChange = { type = option },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            types.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(typeLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.proxy_name_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text(stringResource(R.string.proxy_host)) },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.proxy_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = type == TelegramProxyType.MTPROTO,
                label = CREDENTIALS_LABEL
            ) { mtproto ->
                Column {
                    if (mtproto) {
                        OutlinedTextField(
                            value = secret,
                            onValueChange = { secret = it.trim() },
                            label = { Text(stringResource(R.string.proxy_secret)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.proxy_username_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.proxy_password_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = {
                        onSave(
                            ProxyServer(
                                id = original?.id ?: UUID.randomUUID().toString(),
                                label = label.trim().ifBlank { host.trim() },
                                type = type,
                                host = host.trim(),
                                port = portNumber ?: return@Button,
                                username = username.takeIf {
                                    type != TelegramProxyType.MTPROTO && it.isNotBlank()
                                },
                                password = password.takeIf {
                                    type != TelegramProxyType.MTPROTO && it.isNotBlank()
                                },
                                secret = secret.takeIf {
                                    type == TelegramProxyType.MTPROTO && it.isNotBlank()
                                },
                                isActive = original?.isActive == true
                            )
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                    enabled = valid,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportLinkSheet(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = formSheetState(),
        contentWindowInsets = { SheetTopInset }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = sheetBottomPadding() + 24.dp)
        ) {
            Text(
                text = stringResource(R.string.proxy_paste_link),
                style = MaterialTheme.typography.headlineSmallEmphasized
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.proxy_paste_link_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(stringResource(R.string.proxy_link)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = { onImport(link) },
                    shapes = ButtonDefaults.shapes(),
                    enabled = link.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.proxy_import)) }
            }
        }
    }
}

private const val ROUTING_CARD_KEY = "routing"
private const val EMPTY_STATE_KEY = "empty"
private const val LIST_HEADER_KEY = "header"
private const val TESTING_NOTE_KEY = "testing-note"
private const val COLOR_LABEL = "container"
private const val SUMMARY_LABEL = "summary"
private const val CREDENTIALS_LABEL = "credentials"

private const val INSET_LABEL = "keyboard"
private const val REACHABILITY_LABEL = "reachability"

private val ColorSpec = tween<Color>(durationMillis = 250, easing = FastOutSlowInEasing)
private val InsetSpec = tween<Dp>(durationMillis = 250, easing = FastOutSlowInEasing)

/**
 * Only the top inset is left to the sheet. The bottom one is applied by
 * [sheetBottomPadding] instead, so the keyboard cannot resize the sheet in a
 * single frame.
 */
private val SheetTopInset: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun sheetBottomPadding(): Dp {
    val density = LocalDensity.current
    val keyboard = WindowInsets.imeAnimationTarget.getBottom(density)
    val navigation = WindowInsets.navigationBars.getBottom(density)
    val target = with(density) { maxOf(keyboard, navigation).toDp() }
    val padding by animateDpAsState(
        targetValue = target,
        animationSpec = InsetSpec,
        label = INSET_LABEL
    )
    return padding
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun formSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
)

@Composable
private fun typeLabel(type: TelegramProxyType): String = stringResource(
    when (type) {
        TelegramProxyType.SOCKS5 -> R.string.proxy_type_socks5
        TelegramProxyType.MTPROTO -> R.string.proxy_type_mtproto
        TelegramProxyType.HTTP -> R.string.proxy_type_http
    }
)
