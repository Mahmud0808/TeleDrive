package com.drdisagree.teledrive.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.drdisagree.teledrive.domain.model.DriveChannel
import kotlin.math.absoluteValue

/**
 * Channel picture, or the same placeholder Telegram itself draws when a chat
 * has none: initials over one of seven gradients picked from the chat id.
 * Telegram stores no image in that case, so matching means reproducing its
 * palette rather than theming the circle like the rest of the app.
 */
@Composable
fun ChannelAvatar(
    channel: DriveChannel,
    size: Dp = 48.dp,
    contentDescription: String? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .semantics { contentDescription?.let { this.contentDescription = it } }
    ) {
        if (channel.photoPath != null) {
            AsyncImage(
                model = channel.photoPath,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            return@Box
        }

        val gradient = TELEGRAM_GRADIENTS[(channel.chatId.absoluteValue % 7L).toInt()]
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradient))
        ) {
            Text(
                text = initialsOf(channel.displayName),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * INITIALS_RATIO).sp,
                style = MaterialTheme.typography.titleMedium.copy(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
        }
    }
}

/** First letters of the first two words, as Telegram builds them. */
private fun initialsOf(label: String): String {
    val words = label.trim().split(' ', '\t').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

private val TELEGRAM_GRADIENTS = listOf(
    listOf(Color(0xFFFF845E), Color(0xFFD45246)),
    listOf(Color(0xFFFEBB5B), Color(0xFFF68136)),
    listOf(Color(0xFFB694F9), Color(0xFF6C61DF)),
    listOf(Color(0xFF9AD164), Color(0xFF46BA43)),
    listOf(Color(0xFF5BCBE3), Color(0xFF359AD4)),
    listOf(Color(0xFF5CAFFA), Color(0xFF408ACF)),
    listOf(Color(0xFFFF8AAC), Color(0xFFD95574))
)

private const val INITIALS_RATIO = 0.4f
