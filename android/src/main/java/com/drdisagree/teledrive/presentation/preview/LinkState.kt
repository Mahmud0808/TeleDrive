package com.drdisagree.teledrive.presentation.preview

import com.drdisagree.teledrive.domain.model.LinkMetadata

internal sealed interface LinkState {
    data object Loading : LinkState
    data object Bare : LinkState
    data class Article(val metadata: LinkMetadata) : LinkState
}
