package com.drdisagree.teledrive.core.media

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.drdisagree.teledrive.core.files.MimeTypes
import java.io.File

class AndroidMediaMetadataExtractor : MediaMetadataExtractor {

    override fun extract(file: File, mimeType: String): MediaInfo = when {
        MimeTypes.isImage(mimeType) -> imageInfo(file)
        MimeTypes.isVideo(mimeType) || MimeTypes.isAudio(mimeType) -> videoInfo(file)
        else -> MediaInfo(null, null, null)
    }

    private fun imageInfo(file: File): MediaInfo = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        MediaInfo(
            width = options.outWidth.takeIf { it > 0 },
            height = options.outHeight.takeIf { it > 0 },
            durationMs = null
        )
    }.getOrDefault(MediaInfo(null, null, null))

    private fun videoInfo(file: File): MediaInfo = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            MediaInfo(
                width = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull(),
                height = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull(),
                durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()
            )
        } finally {
            retriever.release()
        }
    }.getOrDefault(MediaInfo(null, null, null))
}
