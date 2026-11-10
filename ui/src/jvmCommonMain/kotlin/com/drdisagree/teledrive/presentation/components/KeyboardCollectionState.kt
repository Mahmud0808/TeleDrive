package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Stable
class KeyboardCollectionState internal constructor(
    currentState: MutableIntState,
    private val scope: CoroutineScope,
    private val cells: () -> List<CollectionCell>,
    private val viewportHeight: () -> Int,
    private val totalCount: () -> Int,
    private val reveal: suspend (Int) -> Unit,
    private val scrollBy: suspend (Float) -> Unit
) {

    var current by currentState
        private set

    internal var columns by mutableIntStateOf(1)
    internal var horizontal = false
    internal var isItem: (Int) -> Boolean = { true }
    private val requesters = HashMap<Int, FocusRequester>()
    private var restorePending = false
    private var focusOnItem = false
    private var focusInItem = false
    private var focusedIndex = -1

    internal fun requester(index: Int): FocusRequester =
        requesters.getOrPut(index) { FocusRequester() }

    internal fun canFocus(index: Int): Boolean {
        val visible = cells()
        val anchor = if (isItem(current) && visible.any { it.index == current }) {
            current
        } else {
            visible.firstOrNull { isItem(it.index) }?.index ?: current
        }
        return index == anchor || index == focusedIndex
    }

    internal fun onFocusChanged(index: Int, focused: Boolean, hasFocus: Boolean) {
        focusOnItem = focused
        focusInItem = hasFocus
        if (hasFocus) focusedIndex = index else if (focusedIndex == index) focusedIndex = -1
        if (hasFocus) current = index
    }

    internal fun handle(event: KeyEvent, shortcuts: CollectionShortcuts): Boolean {
        if (event.type != KeyEventType.KeyDown || !focusInItem) return false
        restorePending = false
        KeyboardHandoff.armed = false
        val command = event.isCtrlPressed || event.isMetaPressed
        val alt = event.isAltPressed
        val plain = !command && !alt && !event.isShiftPressed
        val onItem = plain && focusOnItem
        val vertical = plain && !horizontal
        val across = plain && (horizontal || columns > 1)
        val index = current
        return when {
            onItem && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                KeyboardHandoff.armed = true
                false
            }

            alt && event.key == Key.DirectionLeft -> back(shortcuts)
            vertical && event.key == Key.DirectionDown -> moveRows(1).let { true }
            vertical && event.key == Key.DirectionUp -> moveRows(-1).let { true }
            across && event.key == Key.DirectionRight -> step(1).let { true }
            across && event.key == Key.DirectionLeft -> step(-1).let { true }
            plain && horizontal && event.key == Key.DirectionDown -> step(1)
            plain && horizontal && event.key == Key.DirectionUp -> step(-1)
            onItem && event.key == Key.DirectionRight -> shortcuts.onExpand.fire(index)
            onItem && event.key == Key.DirectionLeft -> shortcuts.onCollapse.fire(index)
            plain && event.key == Key.MoveHome -> focusIndex(firstItem()).let { true }
            plain && event.key == Key.MoveEnd -> focusIndex(lastItem()).let { true }
            vertical && event.key == Key.PageDown -> page(1).let { true }
            vertical && event.key == Key.PageUp -> page(-1).let { true }
            onItem && event.key == Key.Spacebar -> shortcuts.onToggleSelect.fire(index)
            onItem && event.key == Key.Delete -> shortcuts.onDelete.fire(index).also {
                restorePending = it
            }

            onItem && event.key == Key.F2 -> shortcuts.onRename.fire(index)
            plain && event.key == Key.Backspace -> back(shortcuts)
            plain && event.key == Key.Escape -> shortcuts.onClear.fire()
            command && !alt && event.key == Key.A -> shortcuts.onSelectAll.fire()
            command && !alt && focusOnItem && event.key == Key.X -> shortcuts.onCut.fire(index).also {
                restorePending = it
            }

            command && !alt && event.key == Key.V -> shortcuts.onPaste.fire()
            else -> false
        }
    }

    internal fun collectionInfo(itemCount: Int): CollectionInfo = if (horizontal) {
        CollectionInfo(rowCount = 1, columnCount = itemCount)
    } else {
        CollectionInfo(rowCount = (itemCount + columns - 1) / columns, columnCount = columns)
    }

    internal fun itemInfo(itemIndex: Int): CollectionItemInfo = CollectionItemInfo(
        rowIndex = if (horizontal) 0 else itemIndex / columns,
        rowSpan = 1,
        columnIndex = if (horizontal) itemIndex else itemIndex % columns,
        columnSpan = 1
    )

    private fun ((Int) -> Unit)?.fire(index: Int): Boolean {
        val action = this ?: return false
        action(index)
        return true
    }

    private fun back(shortcuts: CollectionShortcuts): Boolean {
        KeyboardHandoff.armed = shortcuts.onBack != null
        return shortcuts.onBack.fire()
    }

    private fun (() -> Unit)?.fire(): Boolean {
        val action = this ?: return false
        action()
        return true
    }

    internal suspend fun claimHandoff() {
        snapshotFlow { totalCount() }.first { it > 0 }
        if (KeyboardHandoff.take()) focusNow(current)
    }

    internal suspend fun onCountChanged() {
        if (!restorePending) return
        restorePending = false
        focusNow(current)
    }

    private fun firstItem(): Int = (0 until totalCount()).firstOrNull(isItem) ?: 0

    private fun lastItem(): Int =
        (totalCount() - 1 downTo 0).firstOrNull(isItem) ?: (totalCount() - 1).coerceAtLeast(0)

    private fun step(direction: Int): Boolean {
        var target = current + direction
        while (target in 0 until totalCount() && !isItem(target)) target += direction
        if (target !in 0 until totalCount()) return false
        focusIndex(target)
        return true
    }

    private fun moveRows(direction: Int) {
        scope.launch {
            val here = cells().firstOrNull { it.index == current }
            if (here == null) {
                focusNow(current)
                return@launch
            }
            val target = rowNeighbour(here, direction) ?: run {
                scrollBy(direction * here.height.toFloat())
                rowNeighbour(here, direction)
            }
            if (target != null) focusNow(target.index)
        }
    }

    private fun rowNeighbour(here: CollectionCell, direction: Int): CollectionCell? {
        val candidates = cells().filter { cell ->
            isItem(cell.index) && if (direction > 0) cell.row > here.row else cell.row < here.row
        }
        if (candidates.isEmpty()) return null
        val row = if (direction > 0) candidates.minOf { it.row } else candidates.maxOf { it.row }
        return candidates.filter { it.row == row }.minBy { abs(it.column - here.column) }
    }

    private fun page(direction: Int) {
        scope.launch {
            val here = cells().firstOrNull { it.index == current }
            scrollBy(direction * viewportHeight().toFloat())
            val visible = cells().filter { isItem(it.index) && it.top >= 0 }
            if (visible.isEmpty()) return@launch
            val row = if (direction > 0) visible.maxOf { it.row } else visible.minOf { it.row }
            val column = here?.column ?: 0
            val target = visible.filter { it.row == row }.minBy { abs(it.column - column) }
            focusNow(target.index)
        }
    }

    private fun focusIndex(index: Int) {
        scope.launch { focusNow(index) }
    }

    private suspend fun focusNow(index: Int) {
        if (totalCount() == 0) return
        var target = index.coerceIn(0, totalCount() - 1)
        while (target > 0 && !isItem(target)) target--
        current = target
        if (cells().none { it.index == target }) reveal(target)
        repeat(FOCUS_ATTEMPTS) {
            runCatching { requester(target).requestFocus() }
            withFrameNanos { }
            withFrameNanos { }
            if (focusOnItem && current == target) return
        }
    }

    private companion object {
        const val FOCUS_ATTEMPTS = 10
    }
}

@Composable
fun rememberKeyboardCollection(
    state: LazyGridState,
    columns: Int,
    isItem: (Int) -> Boolean = { true }
): KeyboardCollectionState {
    val scope = rememberCoroutineScope()
    val currentIsItem by rememberUpdatedState(isItem)
    val currentState = rememberSaveable(state) { mutableIntStateOf(0) }
    return remember(state) {
        KeyboardCollectionState(
            currentState = currentState,
            scope = scope,
            cells = {
                state.layoutInfo.visibleItemsInfo.map {
                    CollectionCell(it.index, it.row, it.column, it.offset.y, it.size.height)
                }
            },
            viewportHeight = { state.layoutInfo.viewportSize.height },
            totalCount = { state.layoutInfo.totalItemsCount },
            reveal = { state.scrollToItem(it) },
            scrollBy = { state.scrollBy(it) }
        )
    }.also {
        it.columns = columns
        it.isItem = { index -> currentIsItem(index) }
        RestoreFocusOnChange(it) { state.layoutInfo.totalItemsCount }
    }
}

@Composable
fun rememberKeyboardCollection(
    state: LazyListState,
    horizontal: Boolean = false,
    isItem: (Int) -> Boolean = { true }
): KeyboardCollectionState {
    val scope = rememberCoroutineScope()
    val currentIsItem by rememberUpdatedState(isItem)
    val currentState = rememberSaveable(state) { mutableIntStateOf(0) }
    return remember(state, horizontal) {
        KeyboardCollectionState(
            currentState = currentState,
            scope = scope,
            cells = {
                state.layoutInfo.visibleItemsInfo.map {
                    if (horizontal) {
                        CollectionCell(it.index, 0, it.index, 0, it.size)
                    } else {
                        CollectionCell(it.index, it.index, 0, it.offset, it.size)
                    }
                }
            },
            viewportHeight = {
                state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset
            },
            totalCount = { state.layoutInfo.totalItemsCount },
            reveal = { state.scrollToItem(it) },
            scrollBy = { state.scrollBy(it) }
        )
    }.also {
        it.columns = 1
        it.horizontal = horizontal
        it.isItem = { index -> currentIsItem(index) }
        RestoreFocusOnChange(it) { state.layoutInfo.totalItemsCount }
    }
}

@Composable
private fun RestoreFocusOnChange(keyboard: KeyboardCollectionState, count: () -> Int) {
    LaunchedEffect(keyboard) { keyboard.claimHandoff() }
    LaunchedEffect(keyboard) {
        snapshotFlow(count).drop(1).collect { keyboard.onCountChanged() }
    }
}
