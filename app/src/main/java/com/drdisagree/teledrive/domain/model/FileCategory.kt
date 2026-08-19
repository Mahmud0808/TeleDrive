package com.drdisagree.teledrive.domain.model

enum class FileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    OTHER;

    companion object {
        fun fromMimeType(mimeType: String): FileCategory = when {
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("video/") -> VIDEO
            mimeType.startsWith("audio/") -> AUDIO
            mimeType == "application/pdf" ||
                mimeType.startsWith("text/") ||
                mimeType.contains("document") ||
                mimeType.contains("spreadsheet") ||
                mimeType.contains("presentation") -> DOCUMENT
            mimeType == "application/zip" ||
                mimeType == "application/x-7z-compressed" ||
                mimeType == "application/vnd.rar" ||
                mimeType == "application/x-tar" ||
                mimeType == "application/gzip" -> ARCHIVE
            else -> OTHER
        }
    }
}
