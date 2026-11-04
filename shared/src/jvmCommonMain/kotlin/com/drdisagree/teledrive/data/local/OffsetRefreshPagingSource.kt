package com.drdisagree.teledrive.data.local

import androidx.paging.PagingSource
import androidx.paging.PagingState

class OffsetRefreshPagingSource<Value : Any>(
    private val source: PagingSource<Int, Value>
) : PagingSource<Int, Value>() {

    init {
        source.registerInvalidatedCallback(::invalidate)
        registerInvalidatedCallback(source::invalidate)
    }

    override val jumpingSupported: Boolean
        get() = source.jumpingSupported

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Value> =
        source.load(params)

    override fun getRefreshKey(state: PagingState<Int, Value>): Int? {
        val anchor = state.anchorPosition ?: return null
        val skipped = if (state.config.enablePlaceholders) {
            0
        } else {
            state.pages.firstOrNull()?.itemsBefore?.takeIf { it != LoadResult.Page.COUNT_UNDEFINED } ?: 0
        }
        return maxOf(0, anchor + skipped - state.config.initialLoadSize / 2)
    }
}
