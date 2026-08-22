package com.drdisagree.teledrive.presentation.note

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.presentation.common.CollectSnackbarMessages
import com.drdisagree.teledrive.presentation.components.BottomBarSnackbarHost
import com.drdisagree.teledrive.presentation.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var body by remember(state.loading) { mutableStateOf(TextFieldValue(state.body)) }

    val linkText = stringResource(R.string.note_link_text)
    val linkUrl = stringResource(R.string.note_link_url)

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)
    LaunchedEffect(Unit) { viewModel.saved.collect { onBack() } }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { BottomBarSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.note_new else R.string.note_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.loading) {
                val canSave = body.text.isNotBlank() && !state.saving
                FloatingActionButton(
                    onClick = { if (canSave) viewModel.save() },
                    containerColor = if (canSave) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (canSave) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
                    }
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.note_save)
                    )
                }
            }
        }
    ) { padding ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                placeholder = {
                    Text(
                        text = stringResource(R.string.note_title_hint),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                enabled = !state.saving,
                colors = borderlessColors(),
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MarkdownToolbar(
                enabled = !state.saving,
                onAction = { action ->
                    body = body.apply(action, linkText, linkUrl)
                    viewModel.setBody(body.text)
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextField(
                value = body,
                onValueChange = {
                    body = it
                    viewModel.setBody(it.text)
                },
                placeholder = { Text(stringResource(R.string.note_body_hint)) },
                enabled = !state.saving,
                colors = borderlessColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = SAVE_BUTTON_CLEARANCE)
            )
        }
    }
}

@Composable
private fun MarkdownToolbar(enabled: Boolean, onAction: (MarkdownAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ToolbarButton(Icons.Filled.FormatBold, R.string.note_bold, enabled) {
            onAction(MarkdownAction.Wrap("**"))
        }
        ToolbarButton(Icons.Filled.FormatItalic, R.string.note_italic, enabled) {
            onAction(MarkdownAction.Wrap("_"))
        }
        ToolbarButton(Icons.Filled.StrikethroughS, R.string.note_strikethrough, enabled) {
            onAction(MarkdownAction.Wrap("~~"))
        }
        ToolbarButton(Icons.Filled.Code, R.string.note_code, enabled) {
            onAction(MarkdownAction.Wrap("`"))
        }
        ToolbarButton(Icons.Filled.Link, R.string.note_link, enabled) {
            onAction(MarkdownAction.Link)
        }
        ToolbarButton(Icons.Filled.Title, R.string.note_heading, enabled) {
            onAction(MarkdownAction.LinePrefix("## "))
        }
        ToolbarButton(
            Icons.AutoMirrored.Filled.FormatListBulleted,
            R.string.note_bullet,
            enabled
        ) {
            onAction(MarkdownAction.LinePrefix("- "))
        }
        ToolbarButton(Icons.Filled.FormatQuote, R.string.note_quote, enabled) {
            onAction(MarkdownAction.LinePrefix("> "))
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun borderlessColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)

private const val DISABLED_ALPHA = 0.38f
private val SAVE_BUTTON_CLEARANCE = 88.dp
