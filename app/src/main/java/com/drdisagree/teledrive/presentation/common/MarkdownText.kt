package com.drdisagree.teledrive.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.drdisagree.teledrive.core.files.Urls
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip

/**
 * Renders the markdown subset the note editor writes: headings, bold, italic,
 * strikethrough, inline code, links, bullets and quotes. Anything it does not
 * recognise stays as written, so no text is ever lost to the parser.
 */
@Composable
fun MarkdownText(
    text: String,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    textScale: Float = 1f
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        text.lines().forEach { line ->
            when {
                line.isBlank() -> Spacer(Modifier.height(8.dp))

                line.startsWith("### ") -> MarkdownLine(
                    line.removePrefix("### "),
                    MaterialTheme.typography.titleSmall.scaledBy(textScale),
                    linkColor,
                    onOpenUrl
                )

                line.startsWith("## ") -> MarkdownLine(
                    line.removePrefix("## "),
                    MaterialTheme.typography.titleMedium.scaledBy(textScale),
                    linkColor,
                    onOpenUrl
                )

                line.startsWith("# ") -> MarkdownLine(
                    line.removePrefix("# "),
                    MaterialTheme.typography.titleLarge.scaledBy(textScale),
                    linkColor,
                    onOpenUrl
                )

                line.startsWith("> ") -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .width(QUOTE_BAR_WIDTH)
                            .heightIn(min = QUOTE_BAR_MIN_HEIGHT)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    MarkdownLine(
                        line.removePrefix("> "),
                        MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            color = quoteColor
                        ).scaledBy(textScale),
                        linkColor,
                        onOpenUrl
                    )
                }

                line.startsWith("- ") || line.startsWith("* ") -> MarkdownLine(
                    "•  " + line.drop(2),
                    MaterialTheme.typography.bodyMedium.scaledBy(textScale),
                    linkColor,
                    onOpenUrl,
                    Modifier.padding(start = 8.dp)
                )

                else -> MarkdownLine(
                    line,
                    MaterialTheme.typography.bodyMedium.scaledBy(textScale),
                    linkColor,
                    onOpenUrl
                )
            }
        }
    }
}

@Composable
private fun MarkdownLine(
    line: String,
    style: TextStyle,
    linkColor: Color,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = inlineMarkdown(line, linkColor)
    val onSurface = MaterialTheme.colorScheme.onSurface
    ClickableText(
        text = annotated,
        style = style.copy(color = style.color.takeOrElse { onSurface }),
        modifier = modifier.padding(vertical = 2.dp),
        onClick = { offset ->
            annotated.getStringAnnotations(URL_TAG, offset, offset)
                .firstOrNull()
                ?.let { onOpenUrl(it.item) }
        }
    )
}

private fun Color.takeOrElse(fallback: () -> Color): Color =
    if (this == Color.Unspecified) fallback() else this

private val INLINE_PATTERN = Regex(
    """\[([^\]]+)\]\(([^)]+)\)|\*\*(.+?)\*\*|~~(.+?)~~|`([^`]+)`|_(.+?)_"""
)

private fun inlineMarkdown(line: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        INLINE_PATTERN.findAll(line).forEach { match ->
            appendPlain(line.substring(cursor, match.range.first), linkColor)
            val (label, url, bold, strike, code, italic) = match.destructured
            when {
                url.isNotEmpty() -> {
                    pushStringAnnotation(URL_TAG, Urls.normalize(url))
                    withLinkStyle(linkColor) { append(label) }
                    pop()
                }

                bold.isNotEmpty() ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }

                strike.isNotEmpty() ->
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(strike)
                    }

                code.isNotEmpty() ->
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }

                italic.isNotEmpty() ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            }
            cursor = match.range.last + 1
        }
        appendPlain(line.substring(cursor), linkColor)
    }

/** Bare links still become tappable, without any markup around them. */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendPlain(
    segment: String,
    linkColor: Color
) {
    var cursor = 0
    Urls.PATTERN.findAll(segment).forEach { match ->
        append(segment.substring(cursor, match.range.first))
        val url = match.value.trimEnd('.', ',', ';', ':')
        pushStringAnnotation(URL_TAG, Urls.normalize(url))
        withLinkStyle(linkColor) { append(url) }
        pop()
        cursor = match.range.first + url.length
    }
    append(segment.substring(cursor))
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withLinkStyle(
    linkColor: Color,
    block: () -> Unit
) {
    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { block() }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyle(
    style: SpanStyle,
    block: () -> Unit
) {
    val index = pushStyle(style)
    block()
    pop(index)
}

/** Pinch scaling applied to every line of a rendered note. */
fun TextStyle.scaledBy(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

private val QUOTE_BAR_WIDTH = 3.dp
private val QUOTE_BAR_MIN_HEIGHT = 20.dp
