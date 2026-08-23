package com.drdisagree.teledrive.core.files

/**
 * Strips Markdown down to the words it decorates. Used where a note's text has
 * to read as a plain label, such as the file name taken from its first line.
 */
object Markdown {

    private val BLOCK_PREFIX = Regex("""^\s*(#{1,6}\s+|>\s+|[-*+]\s+\[[ xX]]\s+|[-*+]\s+|\d+[.)]\s+)""")
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val EMPHASIS = Regex("""(\*\*|__|~~|\*|_|`)(.+?)\1""", RegexOption.DOT_MATCHES_ALL)

    fun plain(text: String): String {
        var result = text
        var passes = 0
        while (passes++ < MAX_PASSES) {
            val before = result
            result = BLOCK_PREFIX.replace(result, "")
            result = IMAGE.replace(result) { it.groupValues[1] }
            result = LINK.replace(result) { it.groupValues[1] }
            result = EMPHASIS.replace(result) { it.groupValues[2] }
            if (result == before) break
        }
        return result.trim()
    }

    private const val MAX_PASSES = 12
}
