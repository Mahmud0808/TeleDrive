package com.drdisagree.teledrive.presentation.preview

import org.jetbrains.compose.resources.StringResource
import com.drdisagree.teledrive.core.media.MediaPart

sealed interface PreviewContent {

    data object Loading : PreviewContent

    data class DownloadProgress(val transferred: Long, val total: Long) : PreviewContent

    /** [model] is a file path or a ByteArray, both renderable by Coil. */
    data class Image(val model: Any) : PreviewContent

    data class LocalMedia(val path: String, val isAudio: Boolean) : PreviewContent

    data class StreamedMedia(
        val remoteFileId: String,
        val isAudio: Boolean,
        val parts: List<MediaPart> = emptyList(),
        val encrypted: Boolean = false
    ) : PreviewContent

    data class Pdf(val path: String) : PreviewContent

    data class PlainText(val text: String, val truncated: Boolean) : PreviewContent

    data class Archive(val entries: List<ArchiveEntry>, val format: String) : PreviewContent

    data class RequiresDownload(val sizeBytes: Long) : PreviewContent

    data class Unsupported(val reasonRes: StringResource) : PreviewContent

    data class Failed(val messageRes: StringResource) : PreviewContent
}
