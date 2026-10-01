package com.drdisagree.teledrive.presentation.note

sealed interface MarkdownAction {
    data class Wrap(val prefix: String, val suffix: String = prefix) : MarkdownAction
    data class LinePrefix(val prefix: String) : MarkdownAction
    data object Link : MarkdownAction
}
