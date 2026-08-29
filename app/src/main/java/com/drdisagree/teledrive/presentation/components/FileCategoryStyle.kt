package com.drdisagree.teledrive.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.presentation.theme.ChartPalette
import com.drdisagree.teledrive.presentation.theme.harmonizedWithPrimary

@Composable
fun FileCategory.label(): String = stringResource(
    when (this) {
        FileCategory.IMAGE -> R.string.common_category_photos
        FileCategory.VIDEO -> R.string.common_category_videos
        FileCategory.AUDIO -> R.string.common_category_audio
        FileCategory.DOCUMENT -> R.string.common_category_documents
        FileCategory.ARCHIVE -> R.string.common_category_archives
        FileCategory.OTHER -> R.string.common_category_other
    }
)

/**
 * Chart color per category. Authored hues keep the categories apart; the
 * harmonization step keeps them in the theme's palette.
 */
@Composable
fun FileCategory.chartColor(): Color = when (this) {
    FileCategory.IMAGE -> ChartPalette.Blue
    FileCategory.VIDEO -> ChartPalette.Violet
    FileCategory.AUDIO -> ChartPalette.Teal
    FileCategory.DOCUMENT -> ChartPalette.Amber
    FileCategory.ARCHIVE -> ChartPalette.Rose
    FileCategory.OTHER -> ChartPalette.Slate
}.harmonizedWithPrimary()
