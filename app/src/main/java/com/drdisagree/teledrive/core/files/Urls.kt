package com.drdisagree.teledrive.core.files

/** URL detection shared by the note preview and thumbnail generation. */
object Urls {

    val PATTERN = Regex("""(?:https?://|www\.)[^\s<>"')\]]+""", RegexOption.IGNORE_CASE)

    fun all(text: String): List<String> =
        PATTERN.findAll(text).map { it.value.trimEnd('.', ',', ';', ':') }.toList()

    /** The single link a body consists of, or null when it holds anything else. */
    fun sole(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        return PATTERN.matchEntire(trimmed)?.value
    }

    /** A bare "www." link needs a scheme before anything will open it. */
    fun normalize(url: String): String =
        if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
}
