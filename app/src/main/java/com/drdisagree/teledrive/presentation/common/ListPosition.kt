package com.drdisagree.teledrive.presentation.common

import android.annotation.SuppressLint
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
 * Where a list was left. Held in saved state rather than in composition: a
 * device that destroys the activity behind the player, which some do while a
 * video decodes, otherwise comes back to the top of the list.
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
 * Paged rows arrive a page at a time, so a deep position is only reachable once
 * enough of them exist. Each new page is another chance to get there, and
 * scrolling to the end asks Paging for the next one.
 */
@SuppressLint("ComposableNaming")
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

@SuppressLint("ComposableNaming")
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
