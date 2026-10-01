package com.drdisagree.teledrive.presentation.common

import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/** So an empty state never flashes before the first rows arrive. */
val LazyPagingItems<*>.isInitialLoad: Boolean
    get() = itemCount == 0 && loadState.refresh is LoadState.Loading
