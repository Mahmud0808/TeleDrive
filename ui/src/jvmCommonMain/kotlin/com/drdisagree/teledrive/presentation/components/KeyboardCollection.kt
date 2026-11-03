package com.drdisagree.teledrive.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics

fun Modifier.keyboardCollection(
    state: KeyboardCollectionState,
    itemCount: Int,
    shortcuts: CollectionShortcuts = CollectionShortcuts(),
    label: String? = null
): Modifier = this
    .semantics {
        collectionInfo = state.collectionInfo(itemCount)
        if (label != null) collectionRole(label, grid = state.columns > 1)
    }
    .onPreviewKeyEvent { event -> state.handle(event, shortcuts) }

fun Modifier.keyboardCollectionItem(
    state: KeyboardCollectionState,
    index: Int,
    itemIndex: Int = index
): Modifier = this
    .focusRequester(state.requester(index))
    .focusProperties { canFocus = state.canFocus(index) }
    .onFocusChanged { state.onFocusChanged(index, it.isFocused, it.hasFocus) }
    .semantics { collectionItemInfo = state.itemInfo(itemIndex) }
