package com.drdisagree.teledrive.presentation.settings

/** State of the passphrase hint stored alongside the key backup. */
sealed interface KeyHint {

    data object Unknown : KeyHint

    data object Loading : KeyHint

    /** No backup could be read from the storage channel. */
    data object Missing : KeyHint

    /** A backup exists; [text] is null when the owner saved no hint. */
    data class Loaded(val text: String?) : KeyHint
}
