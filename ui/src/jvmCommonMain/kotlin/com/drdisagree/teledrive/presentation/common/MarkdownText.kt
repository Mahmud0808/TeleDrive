package com.drdisagree.teledrive.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * Renders the Markdown subset the note editor writes: headings, bold, italic,
 * strikethrough, inline code, links, bullets and quotes. Anything it does not
 * recognize stays as written, so no text is ever lost to the parser.
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
            if (line.isBlank()) {
                Spacer(Modifier.height(8.dp))
            } else {
                MarkdownBlock(line, textScale, linkColor, quoteColor, onOpenUrl)
            }
        }
    }
}

/**
 * One line of Markdown. Block markers can wrap each other, so a quote holding
 * a list holding a heading is rendered by recursing on what is left after the
 * outer marker is taken off.
 */
@Composable
private fun MarkdownBlock(
    line: String,
    textScale: Float,
    linkColor: Color,
    quoteColor: Color,
    onOpenUrl: (String) -> Unit,
    quoted: Boolean = false,
    depth: Int = 0
) {
    val trimmed = line.trimStart()
    val body = MaterialTheme.typography.bodyMedium.let {
        if (quoted) it.copy(fontStyle = FontStyle.Italic, color = quoteColor) else it
    }

    when {
        depth >= MAX_BLOCK_DEPTH ->
            MarkdownLine(line, body.scaledBy(textScale), linkColor, onOpenUrl)

        trimmed.startsWith("> ") -> Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .width(QUOTE_BAR_WIDTH)
                    .heightIn(min = QUOTE_BAR_MIN_HEIGHT)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(10.dp))
            MarkdownBlock(
                line = trimmed.removePrefix("> "),
                textScale = textScale,
                linkColor = linkColor,
                quoteColor = quoteColor,
                onOpenUrl = onOpenUrl,
                quoted = true,
                depth = depth + 1
            )
        }

        trimmed.isBullet() -> Row(
            modifier = Modifier.padding(start = 8.dp + NESTED_INDENT * line.indentDepth())
        ) {
            Text(
                text = bulletFor(line.indentDepth()),
                style = body.scaledBy(textScale),
                color = if (quoted) quoteColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
            MarkdownBlock(
                line = trimmed.drop(2),
                textScale = textScale,
                linkColor = linkColor,
                quoteColor = quoteColor,
                onOpenUrl = onOpenUrl,
                quoted = quoted,
                depth = depth + 1
            )
        }

        else -> {
            val heading = HEADING_PATTERN.find(trimmed)
            if (heading != null) {
                val level = heading.groupValues[1].length
                val style = when (level) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                }
                MarkdownLine(
                    trimmed.removeRange(heading.range),
                    style.scaledBy(textScale),
                    linkColor,
                    onOpenUrl
                )
            } else {
                MarkdownLine(trimmed, body.scaledBy(textScale), linkColor, onOpenUrl)
            }
        }
    }
}

private val HEADING_PATTERN = Regex("""^(#{1,6})\s+""")

private fun String.isBullet(): Boolean =
    trimStart().let { it.startsWith("- ") || it.startsWith("* ") }

/** Leading spaces decide how deep a list item sits, two spaces per level. */
private fun String.indentDepth(): Int =
    (takeWhile { it == ' ' }.length / INDENT_UNIT).coerceAtMost(MAX_NESTING)

private fun bulletFor(depth: Int): String = when (depth % 3) {
    0 -> "•"
    1 -> "◦"
    else -> "▪"
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
    @Suppress("DEPRECATION")
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
    """\[([^\]]+)\]\(([^)]+)\)|\*\*(.+?)\*\*|~~(.+?)~~|`([^`]+)`|_(.+?)_|\*([^*]+)\*"""
)

private fun inlineMarkdown(line: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString { appendMarkdown(line, linkColor, 0) }

/**
 * Styles nest, so what a marker wraps is parsed again instead of appended
 * verbatim: bold holding a link, a link holding code, and so on. Code is the
 * exception, since Markdown inside it is meant to stay as written.
 */
private fun AnnotatedString.Builder.appendMarkdown(
    line: String,
    linkColor: Color,
    depth: Int
) {
    if (depth >= MAX_NESTING) {
        appendPlain(line, linkColor)
        return
    }
    var cursor = 0
    INLINE_PATTERN.findAll(line).forEach { match ->
        appendPlain(line.substring(cursor, match.range.first), linkColor)
        val (label, url, bold, strike, code, italic) = match.destructured
        val starred = match.groupValues[7]
        when {
            url.isNotEmpty() -> {
                pushStringAnnotation(URL_TAG, Urls.normalize(url))
                withLinkStyle(linkColor) { appendMarkdown(label, linkColor, depth + 1) }
                pop()
            }

            bold.isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendMarkdown(bold, linkColor, depth + 1)
                }

            strike.isNotEmpty() ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendMarkdown(strike, linkColor, depth + 1)
                }

            code.isNotEmpty() ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }

            italic.isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendMarkdown(italic, linkColor, depth + 1)
                }

            starred.isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendMarkdown(starred, linkColor, depth + 1)
                }
        }
        cursor = match.range.last + 1
    }
    appendPlain(line.substring(cursor), linkColor)
}

/** Bare links still become tappable, without any markup around them. */
private fun AnnotatedString.Builder.appendPlain(
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

private inline fun AnnotatedString.Builder.withLinkStyle(
    linkColor: Color,
    block: () -> Unit
) {
    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { block() }
}

private inline fun AnnotatedString.Builder.withStyle(
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

private const val MAX_NESTING = 6
private const val MAX_BLOCK_DEPTH = 6
private const val INDENT_UNIT = 2
private val NESTED_INDENT = 16.dp
private val QUOTE_BAR_WIDTH = 3.dp
private val QUOTE_BAR_MIN_HEIGHT = 20.dp
