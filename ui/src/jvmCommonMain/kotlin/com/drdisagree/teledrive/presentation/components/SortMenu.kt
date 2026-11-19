package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.files_sort_ascending
import com.drdisagree.teledrive.resources.files_sort_backup_status
import com.drdisagree.teledrive.resources.files_sort_date_added
import com.drdisagree.teledrive.resources.files_sort_date_modified
import com.drdisagree.teledrive.resources.files_sort_date_taken
import com.drdisagree.teledrive.resources.files_sort_descending
import com.drdisagree.teledrive.resources.files_sort_name
import com.drdisagree.teledrive.resources.files_sort_size
import com.drdisagree.teledrive.resources.files_sort_type
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SortMenu(
    expanded: Boolean,
    current: FileSortField,
    direction: SortDirection,
    onDismiss: () -> Unit,
    onSelect: (FileSortField, SortDirection) -> Unit,
    fields: List<FileSortField> = FILE_SORT_FIELDS
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        fields.forEach { field ->
            val selected = field == current
            val ascending = direction == SortDirection.ASCENDING
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (field) {
                                FileSortField.NAME -> Res.string.files_sort_name
                                FileSortField.SIZE -> Res.string.files_sort_size
                                FileSortField.DATE_MODIFIED -> Res.string.files_sort_date_modified
                                FileSortField.DATE_TAKEN -> Res.string.files_sort_date_taken
                                FileSortField.DATE_ADDED -> Res.string.files_sort_date_added
                                FileSortField.TYPE -> Res.string.files_sort_type
                                FileSortField.BACKUP_STATUS -> Res.string.files_sort_backup_status
                            }
                        )
                    )
                },
                trailingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = if (ascending) {
                                Icons.Filled.ArrowUpward
                            } else {
                                Icons.Filled.ArrowDownward
                            },
                            contentDescription = stringResource(
                                if (ascending) {
                                    Res.string.files_sort_ascending
                                } else {
                                    Res.string.files_sort_descending
                                }
                            ),
                            modifier = Modifier.size(SORT_ICON_SIZE)
                        )
                    }
                } else null,
                onClick = {
                    val newDirection = if (selected && ascending) {
                        SortDirection.DESCENDING
                    } else {
                        SortDirection.ASCENDING
                    }
                    onSelect(field, newDirection)
                }
            )
        }
    }
}

internal val FILE_SORT_FIELDS = FileSortField.entries - FileSortField.DATE_TAKEN

internal val GALLERY_SORT_FIELDS = FileSortField.entries - FileSortField.DATE_MODIFIED

private val SORT_ICON_SIZE = 18.dp
