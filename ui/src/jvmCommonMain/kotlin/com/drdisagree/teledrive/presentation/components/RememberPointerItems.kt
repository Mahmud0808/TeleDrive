package com.drdisagree.teledrive.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.drdisagree.teledrive.presentation.platform.LocalPlatformCapabilities

@Composable
fun rememberPointerItems(
    resetKey: Any?,
    selectionActive: Boolean,
    itemCount: Int,
    clickSelection: Boolean,
    isSelected: (Int) -> Boolean,
    selectOnly: (Int, Boolean) -> Unit,
    toggle: (Int) -> Unit,
    selectRange: (IntRange) -> Unit,
    isSelectable: (Int) -> Boolean,
    open: (Int) -> Unit,
    indexAt: (Offset) -> Int?,
    onContextMenu: (Offset, Boolean) -> Unit
): PointerItems {
    val enabled = LocalPlatformCapabilities.current.usesPointerSelection
    var anchor by rememberSaveable(resetKey) { mutableIntStateOf(-1) }
    LaunchedEffect(selectionActive) { if (!selectionActive) anchor = -1 }
    val pick: (Int) -> Unit = { index ->
        selectOnly(index, true)
        anchor = index
    }
    return PointerItems(clickSelection = enabled && clickSelection) { index ->
        Modifier.pointerSelection(
            enabled = enabled,
            onClick = { click ->
                when (click) {
                    PointerClick.SINGLE -> pick(index)
                    PointerClick.TOGGLE -> {
                        if (clickSelection && isSelected(index)) {
                            selectOnly(index, false)
                        } else {
                            toggle(index)
                        }
                        anchor = index
                    }

                    PointerClick.RANGE -> {
                        val first = (0 until itemCount).firstOrNull(isSelected)
                        val last = (itemCount - 1 downTo 0).firstOrNull(isSelected)
                        val range = when {
                            first == null || last == null -> {
                                val from = anchor.takeIf { it >= 0 } ?: index
                                minOf(from, index)..maxOf(from, index)
                            }

                            index >= first -> first..index
                            else -> index..last
                        }
                        selectRange(range)
                        if (anchor < 0) anchor = index
                    }
                }
            },
            onOpen = { open(index) },
            onDrag = { at ->
                indexAt(at)?.takeIf { it != anchor && isSelectable(it) }?.let(pick)
            },
            onContextMenu = { at ->
                val created = !isSelected(index)
                if (created) pick(index)
                onContextMenu(at, created)
            }
        )
    }
}
