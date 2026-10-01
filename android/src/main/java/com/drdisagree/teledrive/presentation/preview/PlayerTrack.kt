package com.drdisagree.teledrive.presentation.preview

import androidx.media3.common.Tracks

data class PlayerTrack(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val language: String,
    val selected: Boolean,
    val supported: Boolean
)
