package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

fun Modifier.keyboardCollection(
    state: KeyboardCollectionState,
    itemCount: Int,
    shortcuts: CollectionShortcuts = CollectionShortcuts(),
    label: String? = null
): Modifier {
    state.containerTabStop = label != null && collectionContainerIsTabStop
    return this
        .semantics {
            collectionInfo = state.collectionInfo(itemCount)
            if (label != null) collectionRole(label, grid = state.columns > 1)
        }
        .onPreviewKeyEvent { event -> state.handle(event, shortcuts) }
        .then(if (state.containerTabStop) Modifier.containerTabStop(state) else Modifier)
}

fun Modifier.keyboardCollectionItem(
    state: KeyboardCollectionState,
    index: Int,
    itemIndex: Int = index
): Modifier = this
    .focusRequester(state.requester(index))
    .focusProperties { canFocus = state.canFocus(index) }
    .onFocusChanged { state.onFocusChanged(index, it.isFocused, it.hasFocus) }
    .semantics { collectionItemInfo = state.itemInfo(itemIndex) }

private fun Modifier.containerTabStop(state: KeyboardCollectionState): Modifier = composed {
    val outline = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { state.onContainerFocusChanged(it.isFocused, it.hasFocus) }
        .focusable()
        .drawWithContent {
            drawContent()
            if (state.containerFocused) {
                drawRoundRect(
                    color = outline,
                    style = Stroke(width = 2.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            }
        }
}
