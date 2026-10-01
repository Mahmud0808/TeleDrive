package com.drdisagree.teledrive.desktop.media.player

import androidx.compose.ui.layout.ContentScale

internal enum class VideoScaling(val contentScale: ContentScale) {
    FIT(ContentScale.Fit),
    ZOOM(ContentScale.Crop),
    FILL(ContentScale.FillBounds);

    fun next(): VideoScaling = entries[(ordinal + 1) % entries.size]
}
