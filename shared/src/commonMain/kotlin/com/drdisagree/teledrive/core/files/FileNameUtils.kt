package com.drdisagree.teledrive.core.files

object FileNameUtils {

    private val invalidChars = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    fun sanitize(name: String): String =
        invalidChars.replace(name.trim(), "_").take(255).ifEmpty { "unnamed" }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "")

    fun baseNameOf(name: String): String = name.substringBeforeLast('.', name)

    /** Produces "name (1).ext", "name (2).ext", ... until [exists] returns false. */
    fun uniqueName(desired: String, exists: (String) -> Boolean): String {
        if (!exists(desired)) return desired
        val base = baseNameOf(desired)
        val ext = extensionOf(desired)
        var index = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext"
            if (!exists(candidate)) return candidate
            index++
        }
    }
}
