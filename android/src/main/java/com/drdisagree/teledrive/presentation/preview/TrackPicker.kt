package com.drdisagree.teledrive.presentation.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.player_track_unsupported
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

data class PlayerTrack(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val language: String,
    val selected: Boolean,
    val supported: Boolean
)

fun Tracks.playerTracks(type: Int, fallbackPrefix: String): List<PlayerTrack> {
    var position = 0
    return groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).map { index ->
            val format = group.getTrackFormat(index)
            position++
            PlayerTrack(
                group = group,
                index = index,
                label = trackLabel(format, "$fallbackPrefix $position"),
                language = format.language.orEmpty(),
                selected = group.isTrackSelected(index),
                supported = group.isTrackSupported(index)
            )
        }
    }
}

private fun trackLabel(format: Format, fallback: String): String {
    val parts = buildList {
        val language = format.language
            ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
            ?.let { Locale.forLanguageTag(it).displayLanguage }
            ?.takeIf { it.isNotBlank() }
        val name = language ?: format.label
        add(name ?: fallback)
        if (language != null && format.label != null) add(format.label!!)
        channelLabel(format.channelCount)?.let { add(it) }
        codecLabel(format.sampleMimeType)?.let { add(it) }
    }
    return parts.distinct().joinToString(" · ")
}

private fun channelLabel(channels: Int): String? = when {
    channels == Format.NO_VALUE || channels <= 0 -> null
    channels >= 6 -> "${channels - 1}.1"
    else -> "$channels.0"
}

private fun codecLabel(mimeType: String?): String? = when (mimeType) {
    null -> null
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "EAC3"
    MimeTypes.AUDIO_AC4 -> "AC4"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
    MimeTypes.TEXT_VTT -> "WebVTT"
    MimeTypes.APPLICATION_SUBRIP -> "SRT"
    MimeTypes.TEXT_SSA -> "SSA"
    MimeTypes.APPLICATION_TTML -> "TTML"
    MimeTypes.APPLICATION_PGS -> "PGS"
    MimeTypes.APPLICATION_VOBSUB -> "VobSub"
    MimeTypes.APPLICATION_DVBSUBS -> "DVB"
    else -> mimeType.substringAfter('/').uppercase(Locale.ROOT)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPickerSheet(
    title: String,
    tracks: List<PlayerTrack>,
    offLabel: String?,
    offSelected: Boolean,
    onSelectOff: () -> Unit,
    onSelect: (PlayerTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val unsupported = stringResource(Res.string.player_track_unsupported)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        if (offLabel != null) {
            TrackRow(
                label = offLabel,
                detail = null,
                selected = offSelected,
                onClick = onSelectOff
            )
        }
        tracks.forEach { track ->
            TrackRow(
                label = track.label,
                detail = if (track.supported) null else unsupported,
                selected = track.selected,
                onClick = { onSelect(track) }
            )
        }
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .height(8.dp)
        )
    }
}

@Composable
private fun TrackRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
