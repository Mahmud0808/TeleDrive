package com.drdisagree.teledrive.presentation.preview

import androidx.annotation.StringRes
import com.drdisagree.teledrive.core.media.MediaPart

/** Resolved, displayable content for one file in the fullscreen viewer. */
sealed interface PreviewContent {

    data object Loading : PreviewContent

    data class DownloadProgress(val transferred: Long, val total: Long) : PreviewContent

    /** [model] is a file path or a ByteArray, both renderable by Coil. */
    data class Image(val model: Any) : PreviewContent

    /** Local playback from a file on disk. */
    data class LocalMedia(val path: String, val isAudio: Boolean) : PreviewContent

    /** Progressive streaming straight from Telegram. */
    data class StreamedMedia(
        val remoteFileId: String,
        val isAudio: Boolean,
        val parts: List<MediaPart> = emptyList(),
        val encrypted: Boolean = false
    ) : PreviewContent

    data class Pdf(val path: String) : PreviewContent

    data class PlainText(val text: String, val truncated: Boolean) : PreviewContent

    data class Archive(val entries: List<ArchiveEntry>, val format: String) : PreviewContent

    data class ArchiveEntry(
        val name: String,
        val sizeBytes: Long,
        val compressedBytes: Long,
        val isDirectory: Boolean
    )

    /** Too large to fetch inline; user must download explicitly. */
    data class RequiresDownload(val sizeBytes: Long) : PreviewContent

    data class Unsupported(@param:StringRes val reasonRes: Int) : PreviewContent

    data class Failed(@param:StringRes val messageRes: Int) : PreviewContent
}
