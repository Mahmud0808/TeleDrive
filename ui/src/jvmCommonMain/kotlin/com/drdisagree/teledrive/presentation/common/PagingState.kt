package com.drdisagree.teledrive.presentation.common

import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/**
 * True until the first page settles. Screens use it so an empty state never
 * flashes at the moment before the first rows arrive.
 */
val LazyPagingItems<*>.isInitialLoad: Boolean
    get() = itemCount == 0 && loadState.refresh is LoadState.Loading
