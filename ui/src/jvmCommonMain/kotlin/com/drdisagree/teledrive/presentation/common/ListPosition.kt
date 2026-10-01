package com.drdisagree.teledrive.presentation.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle

/**
 * Held in saved state: some devices destroy the activity behind the player, which would reset the
 * list to the top.
 */
class ListPosition(private val state: SavedStateHandle) {

    val index: Int get() = state[KEY_INDEX] ?: 0
    val offset: Int get() = state[KEY_OFFSET] ?: 0

    fun remember(index: Int, offset: Int) {
        state[KEY_INDEX] = index
        state[KEY_OFFSET] = offset
    }

    private companion object {
        const val KEY_INDEX = "list-index"
        const val KEY_OFFSET = "list-offset"
    }
}

/**
 * Each new page is another chance to reach a deep position, and scrolling to the end asks Paging
 * for the next one.
 */
@Composable
fun LazyGridState.rememberPosition(position: ListPosition, itemCount: Int) {
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(this, itemCount, restored) {
        if (restored || itemCount == 0) return@LaunchedEffect
        val target = position.index
        if (target == 0 && position.offset == 0) {
            restored = true
            return@LaunchedEffect
        }
        scrollToItem(minOf(target, itemCount - 1), position.offset)
        if (itemCount > target) restored = true
    }

    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (index, offset) -> if (restored) position.remember(index, offset) }
    }
}

@Composable
fun LazyListState.rememberPosition(position: ListPosition, itemCount: Int) {
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(this, itemCount, restored) {
        if (restored || itemCount == 0) return@LaunchedEffect
        val target = position.index
        if (target == 0 && position.offset == 0) {
            restored = true
            return@LaunchedEffect
        }
        scrollToItem(minOf(target, itemCount - 1), position.offset)
        if (itemCount > target) restored = true
    }

    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (index, offset) -> if (restored) position.remember(index, offset) }
    }
}
