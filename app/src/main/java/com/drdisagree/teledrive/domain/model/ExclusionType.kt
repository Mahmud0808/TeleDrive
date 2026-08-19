package com.drdisagree.teledrive.domain.model

enum class ExclusionType {
    /** Absolute path of a single file. */
    FILE_PATH,

    /** Absolute path of a folder; excludes everything below it. */
    FOLDER_PATH,

    /** File extension without the dot, e.g. "tmp". */
    EXTENSION,

    /** Mime type prefix, e.g. "video/". */
    MIME_TYPE,

    /** Glob-style pattern matched against the absolute path, e.g. "*&#47;Screenshots&#47;*". */
    PATH_PATTERN,

    /** Files larger than the value in bytes. */
    MAX_SIZE,

    /** Hidden files and files inside hidden folders (dot-prefixed or containing .nomedia). */
    HIDDEN
}
