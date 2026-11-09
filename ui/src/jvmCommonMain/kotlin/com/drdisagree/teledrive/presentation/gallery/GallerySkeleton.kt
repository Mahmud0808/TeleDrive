package com.drdisagree.teledrive.presentation.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.presentation.components.skeleton.GridItemSkeleton
import com.drdisagree.teledrive.presentation.components.skeleton.ListItemSkeleton
import com.drdisagree.teledrive.presentation.components.skeleton.SkeletonBlock
import com.drdisagree.teledrive.presentation.components.skeleton.rememberShimmerBrush
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_loading
import org.jetbrains.compose.resources.stringResource

@Composable
fun GallerySkeleton(
    columns: Int,
    list: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val brush = rememberShimmerBrush()
    val loading = stringResource(Res.string.common_loading)
    val spacing = if (list) 0.dp else 6.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (list) 1 else columns),
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = loading },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        userScrollEnabled = false
    ) {
        DAY_GROUPS.forEachIndexed { day, count ->
            item(key = "day-$day", span = { GridItemSpan(maxLineSpan) }) {
                DayHeaderSkeleton(brush)
            }
            items(count) {
                if (list) ListItemSkeleton(brush) else GridItemSkeleton(brush, compact = true)
            }
        }
    }
}

@Composable
private fun DayHeaderSkeleton(brush: Brush) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
            .height(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        SkeletonBlock(brush, Modifier.width(96.dp).height(14.dp))
    }
}

private val DAY_GROUPS = listOf(5, 2, 4)
