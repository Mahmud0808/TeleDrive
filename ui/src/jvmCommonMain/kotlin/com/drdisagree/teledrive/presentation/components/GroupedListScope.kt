package com.drdisagree.teledrive.presentation.components

import androidx.compose.runtime.Composable

class GroupedListScope {

    internal val items = mutableListOf<GroupedListItem>()

    fun add(visible: Boolean = true, content: @Composable () -> Unit) {
        items += GroupedListItem(visible, content)
    }
}
