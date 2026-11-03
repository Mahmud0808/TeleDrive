package com.drdisagree.teledrive.presentation.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.awtRole
import androidx.compose.ui.semantics.contentDescription
import javax.accessibility.AccessibleRole

@OptIn(ExperimentalComposeUiApi::class)
actual fun SemanticsPropertyReceiver.collectionRole(name: String, grid: Boolean) {
    contentDescription = name
    awtRole = if (grid) AccessibleRole.TABLE else AccessibleRole.LIST
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun SemanticsPropertyReceiver.tileRole() {
    awtRole = AccessibleRole.LABEL
}
