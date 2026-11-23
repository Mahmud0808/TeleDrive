package com.drdisagree.teledrive.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

fun Modifier.pointerSelection(
    enabled: Boolean,
    onClick: (PointerClick) -> Unit,
    onOpen: () -> Unit,
    onDrag: (Offset) -> Unit,
    onContextMenu: (Offset) -> Unit
): Modifier = if (!enabled) this else composed {
    val click by rememberUpdatedState(onClick)
    val open by rememberUpdatedState(onOpen)
    val drag by rememberUpdatedState(onDrag)
    val contextMenu by rememberUpdatedState(onContextMenu)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    this
        .onGloballyPositioned { coordinates[0] = it }
        .pointerInput(Unit) {
            val doubleClickMs = viewConfiguration.doubleTapTimeoutMillis
            awaitPointerEventScope {
                var lastClickAt = 0L
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Press) continue
                    val change = event.changes.firstOrNull() ?: continue
                    val modifiers = event.keyboardModifiers
                    when {
                        event.buttons.isSecondaryPressed -> {
                            change.consume()
                            val origin = coordinates[0]?.localToRoot(change.position)
                            consumeUntilReleased()
                            contextMenu(origin ?: change.position)
                        }

                        event.buttons.isPrimaryPressed && modifiers.isShiftPressed -> {
                            change.consume()
                            consumeUntilReleased()
                            click(PointerClick.RANGE)
                        }

                        event.buttons.isPrimaryPressed &&
                                (modifiers.isCtrlPressed || modifiers.isMetaPressed) -> {
                            change.consume()
                            consumeUntilReleased()
                            click(PointerClick.TOGGLE)
                        }

                        event.buttons.isPrimaryPressed -> {
                            change.consume()
                            val now = change.uptimeMillis
                            val second = now - lastClickAt <= doubleClickMs
                            lastClickAt = if (second) 0L else now
                            if (second) {
                                consumeUntilReleased()
                                open()
                            } else {
                                click(PointerClick.SINGLE)
                                followUntilReleased { position ->
                                    drag(coordinates[0]?.localToRoot(position) ?: position)
                                }
                            }
                        }
                    }
                }
            }
        }
}

fun Modifier.pointerBackground(
    enabled: Boolean,
    onClick: () -> Unit,
    onContextMenu: (Offset) -> Unit,
    onBack: (() -> Unit)?
): Modifier = if (!enabled) this else composed {
    val click by rememberUpdatedState(onClick)
    val contextMenu by rememberUpdatedState(onContextMenu)
    val back by rememberUpdatedState(onBack)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    this
        .onGloballyPositioned { coordinates[0] = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.type != PointerEventType.Press) continue
                    val change = event.changes.firstOrNull() ?: continue
                    when {
                        event.buttons.isBackPressed -> back?.invoke()
                        change.isConsumed -> Unit
                        event.buttons.isSecondaryPressed -> {
                            change.consume()
                            val origin = coordinates[0]?.localToRoot(change.position)
                            contextMenu(origin ?: change.position)
                        }

                        event.buttons.isPrimaryPressed -> click()
                    }
                }
            }
        }
}

private suspend fun AwaitPointerEventScope.followUntilReleased(onMove: (Offset) -> Unit) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        val change = event.changes.firstOrNull() ?: return
        if (!change.pressed) return
        if (change.position != change.previousPosition) onMove(change.position)
    }
}

private suspend fun AwaitPointerEventScope.consumeUntilReleased() {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) return
    }
}
