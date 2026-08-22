package com.drdisagree.teledrive.presentation.note

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Markup a toolbar button applies around or before the current selection. */
sealed interface MarkdownAction {
    data class Wrap(val prefix: String, val suffix: String = prefix) : MarkdownAction
    data class LinePrefix(val prefix: String) : MarkdownAction
    data object Link : MarkdownAction
}

fun TextFieldValue.apply(action: MarkdownAction, placeholder: String, url: String): TextFieldValue =
    when (action) {
        is MarkdownAction.Wrap -> wrap(action.prefix, action.suffix, placeholder)
        is MarkdownAction.LinePrefix -> prefixLine(action.prefix)
        MarkdownAction.Link -> wrapLink(placeholder, url)
    }

private fun TextFieldValue.wrap(
    prefix: String,
    suffix: String,
    placeholder: String
): TextFieldValue {
    val selected = text.substring(selection.min, selection.max)
    val body = selected.ifEmpty { placeholder }
    val updated = text.replaceRange(selection.min, selection.max, "$prefix$body$suffix")
    val start = selection.min + prefix.length
    return copy(text = updated, selection = TextRange(start, start + body.length))
}

private fun TextFieldValue.wrapLink(placeholder: String, url: String): TextFieldValue {
    val selected = text.substring(selection.min, selection.max)
    val label = selected.ifEmpty { placeholder }
    val markup = "[$label]($url)"
    val updated = text.replaceRange(selection.min, selection.max, markup)
    val urlStart = selection.min + label.length + 3
    return copy(text = updated, selection = TextRange(urlStart, urlStart + url.length))
}

/** Marks the whole line, so the caret can sit anywhere within it. */
private fun TextFieldValue.prefixLine(prefix: String): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    if (text.startsWith(prefix, lineStart)) {
        val updated = text.removeRange(lineStart, lineStart + prefix.length)
        return copy(
            text = updated,
            selection = TextRange((selection.min - prefix.length).coerceAtLeast(lineStart))
        )
    }
    val updated = text.replaceRange(lineStart, lineStart, prefix)
    return copy(text = updated, selection = TextRange(selection.min + prefix.length))
}
