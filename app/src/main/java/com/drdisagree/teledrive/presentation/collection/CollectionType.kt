package com.drdisagree.teledrive.presentation.collection

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.drdisagree.teledrive.R

enum class CollectionType(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
    @param:StringRes val emptyMessageRes: Int
) {
    FAVORITES(
        titleRes = R.string.collection_favorites_title,
        subtitleRes = R.string.collection_favorites_subtitle,
        icon = Icons.Filled.Star,
        emptyMessageRes = R.string.collection_favorites_empty
    ),
    ARCHIVED(
        titleRes = R.string.collection_archived_title,
        subtitleRes = R.string.collection_archived_subtitle,
        icon = Icons.Filled.Archive,
        emptyMessageRes = R.string.collection_archived_empty
    ),
    HIDDEN(
        titleRes = R.string.collection_hidden_title,
        subtitleRes = R.string.collection_hidden_subtitle,
        icon = Icons.Filled.VisibilityOff,
        emptyMessageRes = R.string.collection_hidden_empty
    )
}
