package com.drdisagree.teledrive.presentation.components

import androidx.compose.ui.semantics.SemanticsPropertyReceiver

actual fun SemanticsPropertyReceiver.collectionRole(name: String, grid: Boolean) = Unit

actual fun SemanticsPropertyReceiver.tileRole() = Unit
