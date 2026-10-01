package com.drdisagree.teledrive.core.files

object Urls {

    val PATTERN = Regex("""(?:https?://|www\.)[^\s<>"')\]]+""", RegexOption.IGNORE_CASE)

    fun all(text: String): List<String> =
        PATTERN.findAll(text).map { it.value.trimEnd('.', ',', ';', ':') }.toList()

    fun sole(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        return PATTERN.matchEntire(trimmed)?.value
    }

    fun normalize(url: String): String =
        if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
}
