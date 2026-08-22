package com.drdisagree.teledrive.presentation.common

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.drdisagree.teledrive.core.files.Urls

private val URL_PATTERN = Urls.PATTERN

const val URL_TAG = "url"

/** Every link found in [text], in the order they appear. */
fun urlsIn(text: String): List<String> = Urls.all(text)

/** The single link a body consists of, or null when it holds anything else. */
fun soleUrlOf(text: String): String? = Urls.sole(text)

/** Renders [text] with its links underlined and clickable. */
@Composable
fun LinkedText(
    text: String,
    style: TextStyle,
    linkColor: Color,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text, linkColor) { annotateLinks(text, linkColor) }
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(URL_TAG, offset, offset)
                .firstOrNull()
                ?.let { onOpenUrl(it.item) }
        }
    )
}

private fun annotateLinks(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    URL_PATTERN.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val url = match.value.trimEnd('.', ',', ';', ':')
        pushStringAnnotation(URL_TAG, normalizeUrl(url))
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append(url)
        }
        pop()
        cursor = match.range.first + url.length
    }
    append(text.substring(cursor))
}

/** A bare "www." link needs a scheme before anything will open it. */
fun normalizeUrl(url: String): String = Urls.normalize(url)
