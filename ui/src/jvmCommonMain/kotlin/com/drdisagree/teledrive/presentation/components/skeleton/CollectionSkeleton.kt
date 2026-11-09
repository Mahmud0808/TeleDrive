package com.drdisagree.teledrive.presentation.components.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_loading
import org.jetbrains.compose.resources.stringResource

@Composable
fun CollectionSkeleton(
    columns: Int,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    compactTiles: Boolean = false,
    itemCount: Int = SKELETON_ITEMS,
    item: @Composable (Brush) -> Unit = { brush ->
        if (columns == 1) {
            ListItemSkeleton(brush)
        } else {
            GridItemSkeleton(brush, compact = compactTiles)
        }
    }
) {
    val brush = rememberShimmerBrush()
    val loading = stringResource(Res.string.common_loading)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = loading },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        userScrollEnabled = false
    ) {
        items(itemCount) { item(brush) }
    }
}

private const val SKELETON_ITEMS = 7
