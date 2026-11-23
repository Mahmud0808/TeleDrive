package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.util.fastFirstOrNull

class LazyIndexLocator {

    private var origin = Offset.Zero

    val modifier: Modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() }

    fun indexAt(state: LazyGridState, root: Offset): Int? {
        val local = root - origin
        return state.layoutInfo.visibleItemsInfo.fastFirstOrNull { item ->
            local.x >= item.offset.x &&
                    local.x <= item.offset.x + item.size.width &&
                    local.y >= item.offset.y &&
                    local.y <= item.offset.y + item.size.height
        }?.index
    }

    fun indexAt(state: LazyListState, root: Offset): Int? {
        val local = root - origin
        return state.layoutInfo.visibleItemsInfo.fastFirstOrNull { item ->
            local.y >= item.offset && local.y <= item.offset + item.size
        }?.index
    }
}
