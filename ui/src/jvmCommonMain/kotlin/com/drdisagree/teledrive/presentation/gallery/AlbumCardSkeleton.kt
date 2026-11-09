package com.drdisagree.teledrive.presentation.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.presentation.components.skeleton.SkeletonBlock

@Composable
fun AlbumCardSkeleton(brush: Brush, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(6.dp)
    ) {
        SkeletonBlock(
            brush = brush,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = MaterialTheme.shapes.medium
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 2.dp, vertical = 6.dp)
                .height(36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.7f).height(12.dp))
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.4f).height(10.dp))
        }
    }
}
