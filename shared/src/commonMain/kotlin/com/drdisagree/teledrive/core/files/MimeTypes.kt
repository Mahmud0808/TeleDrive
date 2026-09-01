package com.drdisagree.teledrive.core.files

object MimeTypes {

    const val GENERIC = "application/octet-stream"

    fun fromFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return GENERIC
        return platformMimeTypeFromExtension(extension) ?: when (extension) {
            "mkv" -> "video/x-matroska"
            "flac" -> "audio/flac"
            "opus" -> "audio/opus"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "yml", "yaml" -> "application/yaml"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "apk" -> "application/vnd.android.package-archive"
            else -> GENERIC
        }
    }

    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")

    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")

    fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")

    fun isPdf(mimeType: String): Boolean = mimeType == "application/pdf"

    fun isMarkdown(mimeType: String): Boolean = mimeType == "text/markdown"

    fun isApk(mimeType: String): Boolean = mimeType == "application/vnd.android.package-archive"

    fun isText(mimeType: String): Boolean =
        mimeType.startsWith("text/") ||
                mimeType == "application/json" ||
                mimeType == "application/xml" ||
                mimeType == "application/yaml"

    fun isArchive(mimeType: String): Boolean = mimeType in setOf(
        "application/zip",
        "application/x-7z-compressed",
        "application/vnd.rar",
        "application/x-tar",
        "application/gzip"
    )
}
