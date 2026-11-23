package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun PointerMenu(at: Offset?, actions: List<MenuAction>, onDismiss: (Boolean) -> Unit) {
    if (at == null || actions.isEmpty()) return
    val acted = remember(at) { booleanArrayOf(false) }
    val tracked = remember(actions, acted) { actions.map { it.tracked(acted) } }
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
        ActionMenu(
            expanded = true,
            onDismissRequest = { onDismiss(acted[0]) },
            actions = tracked
        )
    }
}

private fun MenuAction.tracked(acted: BooleanArray): MenuAction = copy(
    children = children.map { it.tracked(acted) },
    onClick = {
        acted[0] = true
        onClick()
    }
)
