package com.drdisagree.teledrive.presentation.trash

import com.drdisagree.teledrive.domain.model.TrashItem

/**
 * Only top-level rows can be selected, because restoring or deleting a folder takes its contents
 * along.
 */
data class TrashRow(
    val item: TrashItem,
    val depth: Int,
    val expandable: Boolean,
    val expanded: Boolean
) {
    val selectable: Boolean get() = depth == 0
    val key: String get() = "${item.id}-$depth"
}
