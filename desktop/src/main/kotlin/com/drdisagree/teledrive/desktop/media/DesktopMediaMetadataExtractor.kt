package com.drdisagree.teledrive.desktop.media

import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.media.CaptureTime
import com.drdisagree.teledrive.core.media.MediaInfo
import com.drdisagree.teledrive.core.media.MediaMetadataExtractor
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.mov.QuickTimeDirectory
import com.drew.metadata.mp4.Mp4Directory
import java.io.File
import javax.imageio.ImageIO

class DesktopMediaMetadataExtractor : MediaMetadataExtractor {

    override fun extract(file: File, mimeType: String): MediaInfo = when {
        MimeTypes.isImage(mimeType) -> imageSize(file).copy(capturedAt = capturedAt(file))
        MimeTypes.isVideo(mimeType) -> MediaInfo(null, null, null, capturedAt(file))
        else -> MediaInfo(null, null, null)
    }

    private fun imageSize(file: File): MediaInfo = runCatching {
        ImageIO.createImageInputStream(file).use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@runCatching MediaInfo(null, null, null)
            val reader = readers.next()
            try {
                reader.input = stream
                MediaInfo(reader.getWidth(0), reader.getHeight(0), null)
            } finally {
                reader.dispose()
            }
        }
    }.getOrDefault(MediaInfo(null, null, null))

    private fun capturedAt(file: File): Long? = runCatching {
        val metadata = ImageMetadataReader.readMetadata(file)
        exifCapturedAt(metadata) ?: containerCreatedAt(metadata)
    }.getOrNull()

    private fun exifCapturedAt(metadata: Metadata): Long? =
        metadata.getDirectoriesOfType(ExifSubIFDDirectory::class.java).firstNotNullOfOrNull {
            CaptureTime.fromExif(
                it.getString(ExifDirectoryBase.TAG_DATETIME_ORIGINAL),
                it.getString(ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL)
            ) ?: CaptureTime.fromExif(
                it.getString(ExifDirectoryBase.TAG_DATETIME_DIGITIZED),
                it.getString(ExifDirectoryBase.TAG_TIME_ZONE_DIGITIZED)
            )
        }

    private fun containerCreatedAt(metadata: Metadata): Long? {
        val mp4 = metadata.getFirstDirectoryOfType(Mp4Directory::class.java)
            ?.getDate(Mp4Directory.TAG_CREATION_TIME)
        val quickTime = metadata.getFirstDirectoryOfType(QuickTimeDirectory::class.java)
            ?.getDate(QuickTimeDirectory.TAG_CREATION_TIME)
        return CaptureTime.plausible((mp4 ?: quickTime)?.time)
    }
}
