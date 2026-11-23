package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

sealed interface ScrollerTarget {
    val canScrollForward: Boolean
    val canScrollBackward: Boolean
    val isScrollInProgress: Boolean
    val totalItems: Int
    val firstVisibleIndex: Int
    val viewport: Int
    val padding: Int
    val spacing: Int
    fun lines(): List<ScrollLine>
    suspend fun scrollToItem(index: Int, offset: Int)

    class Grid(private val state: LazyGridState) : ScrollerTarget {
        override val canScrollForward get() = state.canScrollForward
        override val canScrollBackward get() = state.canScrollBackward
        override val isScrollInProgress get() = state.isScrollInProgress
        override val totalItems get() = state.layoutInfo.totalItemsCount
        override val firstVisibleIndex get() = state.firstVisibleItemIndex
        override val viewport get() = state.layoutInfo.viewportSize.height
        override val padding
            get() = state.layoutInfo.beforeContentPadding + state.layoutInfo.afterContentPadding
        override val spacing get() = state.layoutInfo.mainAxisItemSpacing

        override fun lines(): List<ScrollLine> = state.layoutInfo.visibleItemsInfo
            .groupBy { it.row }
            .toSortedMap()
            .values
            .map { row ->
                ScrollLine(
                    firstIndex = row.minOf { it.index },
                    itemCount = row.size,
                    top = row.minOf { it.offset.y },
                    height = row.maxOf { it.size.height } + spacing
                )
            }

        override suspend fun scrollToItem(index: Int, offset: Int) {
            state.scrollToItem(index, offset)
        }
    }

    class Linear(private val state: LazyListState) : ScrollerTarget {
        override val canScrollForward get() = state.canScrollForward
        override val canScrollBackward get() = state.canScrollBackward
        override val isScrollInProgress get() = state.isScrollInProgress
        override val totalItems get() = state.layoutInfo.totalItemsCount
        override val firstVisibleIndex get() = state.firstVisibleItemIndex
        override val viewport get() = state.layoutInfo.viewportSize.height
        override val padding
            get() = state.layoutInfo.beforeContentPadding + state.layoutInfo.afterContentPadding
        override val spacing get() = state.layoutInfo.mainAxisItemSpacing

        override fun lines(): List<ScrollLine> = state.layoutInfo.visibleItemsInfo.map { item ->
            ScrollLine(
                firstIndex = item.index,
                itemCount = 1,
                top = item.offset,
                height = item.size + spacing
            )
        }

        override suspend fun scrollToItem(index: Int, offset: Int) {
            state.scrollToItem(index, offset)
        }
    }
}
