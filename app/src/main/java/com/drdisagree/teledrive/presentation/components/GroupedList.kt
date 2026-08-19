package com.drdisagree.teledrive.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class GroupedListScope {

    internal val items = mutableListOf<GroupedListItem>()

    fun add(visible: Boolean = true, content: @Composable () -> Unit) {
        items += GroupedListItem(visible, content)
    }
}

internal data class GroupedListItem(
    val visible: Boolean,
    val content: @Composable () -> Unit
)

/**
 * Rows rendered as one grouped container: outer corners rounded, inner corners
 * tightened, hairline gaps between rows. Hidden rows animate out and the
 * neighbouring corners morph to become the new group edge.
 */
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    content: GroupedListScope.() -> Unit
) {
    val items = GroupedListScope().apply(content).items
    if (items.isEmpty()) return

    val visibleIndices = items.mapIndexedNotNull { index, item ->
        index.takeIf { item.visible }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        items.forEachIndexed { index, item ->
            val position = visibleIndices.indexOf(index)
            val shape by animateShapeCorners(
                isFirst = position == 0,
                isLast = position >= 0 && position == visibleIndices.lastIndex
            )
            AnimatedVisibility(
                visible = item.visible,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) +
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) +
                    fadeOut()
            ) {
                Column {
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item.content()
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun animateShapeCorners(isFirst: Boolean, isLast: Boolean): State<Shape> {
    val top by animateDpAsState(
        targetValue = if (isFirst) OUTER_CORNER else INNER_CORNER,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "topCorner"
    )
    val bottom by animateDpAsState(
        targetValue = if (isLast) OUTER_CORNER else INNER_CORNER,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bottomCorner"
    )
    return remember {
        derivedStateOf {
            RoundedCornerShape(
                topStart = top,
                topEnd = top,
                bottomStart = bottom,
                bottomEnd = bottom
            )
        }
    }
}

private val OUTER_CORNER = 24.dp
private val INNER_CORNER = 6.dp
