package com.drdisagree.teledrive.presentation.components.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.presentation.components.LocalCompactLayout

@Composable
fun ListItemSkeleton(brush: Brush, modifier: Modifier = Modifier) {
    val compact = LocalCompactLayout.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBlock(
            brush = brush,
            modifier = Modifier.size(if (compact) 38.dp else 48.dp),
            shape = MaterialTheme.shapes.medium
        )
        Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
        Column(
            modifier = Modifier.height(if (compact) 24.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.6f).height(14.dp))
            if (!compact) {
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.3f).height(10.dp))
            }
        }
    }
}
