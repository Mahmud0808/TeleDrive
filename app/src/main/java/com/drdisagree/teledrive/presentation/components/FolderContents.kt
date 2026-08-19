package com.drdisagree.teledrive.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.domain.model.DriveFolder

@Composable
fun DriveFolder.contentsLabel(): String {
    val parts = buildList {
        if (folderCount > 0) {
            add(pluralStringResource(R.plurals.folder_count, folderCount, folderCount))
        }
        if (fileCount > 0) {
            add(pluralStringResource(R.plurals.file_count, fileCount, fileCount))
        }
    }
    return if (parts.isEmpty()) stringResource(R.string.common_empty) else parts.joinToString(" · ")
}
