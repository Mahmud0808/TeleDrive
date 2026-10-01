package com.drdisagree.teledrive.presentation.settings

sealed interface KeyHint {

    data object Unknown : KeyHint

    data object Loading : KeyHint

    data object Missing : KeyHint

    /** [text] is null when the owner saved no hint. */
    data class Loaded(val text: String?) : KeyHint
}
