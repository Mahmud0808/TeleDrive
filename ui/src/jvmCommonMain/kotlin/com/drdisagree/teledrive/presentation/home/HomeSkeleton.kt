package com.drdisagree.teledrive.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.presentation.collection.CollectionType
import com.drdisagree.teledrive.presentation.components.skeleton.SkeletonBlock
import com.drdisagree.teledrive.presentation.components.skeleton.rememberShimmerBrush
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_loading
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeSkeleton(
    sections: HomeSections,
    showBackupCard: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val brush = rememberShimmerBrush()
    val loading = stringResource(Res.string.common_loading)
    val linkCount = CollectionType.entries.count { collection ->
        when (collection) {
            CollectionType.ARCHIVED -> sections.showArchived
            CollectionType.HIDDEN -> sections.showHidden
            else -> true
        }
    } + EXTRA_LINKS

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = loading },
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        if (showBackupCard) {
            item {
                SkeletonBlock(
                    brush = brush,
                    modifier = Modifier.fillMaxWidth().height(BACKUP_CARD_HEIGHT),
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
        if (sections.showRecent) {
            item {
                Column {
                    SectionHeaderSkeleton(brush)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(RECENT_TILES) { RecentTileSkeleton(brush) }
                    }
                }
            }
        }
        item {
            Column {
                SectionHeaderSkeleton(brush)
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(
                    brush = brush,
                    modifier = Modifier.fillMaxWidth().height(STORAGE_CARD_HEIGHT),
                    shape = MaterialTheme.shapes.large
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(linkCount) { CollectionRowSkeleton(brush) }
            }
        }
    }
}

@Composable
private fun SectionHeaderSkeleton(brush: Brush) {
    SkeletonBlock(brush, Modifier.width(96.dp).height(18.dp))
}

@Composable
private fun RecentTileSkeleton(brush: Brush) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(6.dp)
    ) {
        SkeletonBlock(brush, Modifier.size(100.dp), MaterialTheme.shapes.medium)
        Spacer(Modifier.height(6.dp))
        SkeletonBlock(brush, Modifier.fillMaxWidth(0.8f).height(12.dp))
    }
}

@Composable
private fun CollectionRowSkeleton(brush: Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBlock(brush, Modifier.size(24.dp), MaterialTheme.shapes.small)
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.height(40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.5f).height(14.dp))
            SkeletonBlock(brush, Modifier.fillMaxWidth(0.7f).height(10.dp))
        }
    }
}

private const val EXTRA_LINKS = 2
private const val RECENT_TILES = 4
private val BACKUP_CARD_HEIGHT = 152.dp
private val STORAGE_CARD_HEIGHT = 200.dp
