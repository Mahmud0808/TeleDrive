package com.drdisagree.teledrive.desktop.media.player

import uk.co.caprica.vlcj.media.AudioTrackInfo
import uk.co.caprica.vlcj.media.TextTrackInfo
import uk.co.caprica.vlcj.media.TrackInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.util.Locale

internal data class VlcTrack(
    val id: Int,
    val label: String,
    val language: String,
    val selected: Boolean
)

internal const val VLC_TRACK_DISABLED = -1

internal fun MediaPlayer.audioTracks(fallbackPrefix: String): List<VlcTrack> {
    val info = runCatching { media().info()?.audioTracks().orEmpty() }
        .getOrDefault(emptyList<AudioTrackInfo>())
    val current = runCatching { audio().track() }.getOrDefault(VLC_TRACK_DISABLED)
    val descriptions = runCatching { audio().trackDescriptions() }.getOrDefault(emptyList())
    return descriptions.filter { it.id() != VLC_TRACK_DISABLED }
        .mapIndexed { position, description ->
            val track = info.firstOrNull { it.id() == description.id() }
            VlcTrack(
                id = description.id(),
                label = trackLabel(
                    description = description.description(),
                    track = track,
                    channels = track?.channels() ?: 0,
                    fallback = "$fallbackPrefix ${position + 1}"
                ),
                language = normalizeLanguage(track?.language()),
                selected = description.id() == current
            )
        }
}

internal fun MediaPlayer.textTracks(fallbackPrefix: String): List<VlcTrack> {
    val info = runCatching { media().info()?.textTracks().orEmpty() }
        .getOrDefault(emptyList<TextTrackInfo>())
    val current = runCatching { subpictures().track() }.getOrDefault(VLC_TRACK_DISABLED)
    val descriptions = runCatching { subpictures().trackDescriptions() }.getOrDefault(emptyList())
    return descriptions.filter { it.id() != VLC_TRACK_DISABLED }
        .mapIndexed { position, description ->
            val track = info.firstOrNull { it.id() == description.id() }
            VlcTrack(
                id = description.id(),
                label = trackLabel(
                    description = description.description(),
                    track = track,
                    channels = 0,
                    fallback = "$fallbackPrefix ${position + 1}"
                ),
                language = normalizeLanguage(track?.language()),
                selected = description.id() == current
            )
        }
}

private fun trackLabel(
    description: String?,
    track: TrackInfo?,
    channels: Int,
    fallback: String
): String {
    val language = normalizeLanguage(track?.language())
        .takeIf { it.isNotEmpty() }
        ?.let { Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() }
    val parts = buildList {
        add(language ?: description?.takeIf { it.isNotBlank() } ?: fallback)
        track?.description()?.takeIf { it.isNotBlank() }?.let { add(it) }
        channelLabel(channels)?.let { add(it) }
        track?.codecDescription()?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.distinct().joinToString(" · ")
}

private fun channelLabel(channels: Int): String? = when {
    channels <= 0 -> null
    channels >= 6 -> "${channels - 1}.1"
    else -> "$channels.0"
}

internal fun normalizeLanguage(raw: String?): String {
    val value = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when {
        value.isEmpty() || value == "und" || value == "undefined" -> ""
        value.length == 2 -> value
        value.length == 3 -> Locale.getISOLanguages()
            .firstOrNull { Locale.forLanguageTag(it).isO3Language == value } ?: value

        else -> Locale.getISOLanguages().firstOrNull {
            Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).equals(value, true)
        }.orEmpty()
    }
}
