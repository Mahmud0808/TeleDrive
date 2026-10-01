package com.drdisagree.teledrive.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Callers leave out actions that do not apply, so the menu stays as short as the selection allows.
 */
@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<MenuAction>,
    modifier: Modifier = Modifier
) {
    val stack = remember { mutableStateListOf<MenuAction>() }

    // Reset after the dismiss animation, so the menu does not visibly rewind.
    LaunchedEffect(expanded) {
        if (!expanded) {
            delay(RESET_DELAY_MS.milliseconds)
            stack.clear()
        }
    }

    val parent = stack.lastOrNull()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = EDGE_INSET,
        modifier = modifier
    ) {
        if (parent != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = parent.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(ICON_SIZE)
                    )
                },
                onClick = { stack.removeAt(stack.lastIndex) }
            )
            HorizontalDivider()
        }
        (parent?.children ?: actions).forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = action.icon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(ICON_SIZE)
                        )
                    }
                },
                trailingIcon = if (action.children.isEmpty()) {
                    null
                } else {
                    {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(CHEVRON_SIZE)
                        )
                    }
                },
                onClick = {
                    if (action.children.isEmpty()) {
                        action.onClick()
                        onDismissRequest()
                    } else {
                        stack.add(action)
                    }
                }
            )
        }
    }
}

// Pulls the popup inward from the top bar edge; DropdownMenu mirrors it for RTL, so do not negate
// it.
private val EDGE_INSET = DpOffset(x = (-8).dp, y = 0.dp)
private val ICON_SIZE = 20.dp
private val CHEVRON_SIZE = 18.dp
private const val RESET_DELAY_MS = 200L
