package com.drdisagree.teledrive.presentation.trash

import com.drdisagree.teledrive.domain.model.TrashItem

internal data class TrashTreeCache(
    val children: Map<String, List<TrashItem>> = emptyMap(),
    val counts: Map<String, Int> = emptyMap()
)
