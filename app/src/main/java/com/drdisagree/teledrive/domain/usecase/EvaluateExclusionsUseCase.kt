package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.model.Exclusion
import com.drdisagree.teledrive.domain.model.ExclusionType
import java.util.Locale
import javax.inject.Inject

/**
 * Pure exclusion matching. Paths are compared case-insensitively because
 * Android external storage is case-insensitive in practice.
 */
class EvaluateExclusionsUseCase @Inject constructor() {

    data class Candidate(
        val absolutePath: String,
        val sizeBytes: Long,
        val mimeType: String,
        val isHidden: Boolean
    )

    operator fun invoke(candidate: Candidate, exclusions: List<Exclusion>): Boolean {
        val path = candidate.absolutePath.replace('\\', '/').lowercase(Locale.ROOT)
        val fileName = path.substringAfterLast('/')
        for (exclusion in exclusions) {
            if (!exclusion.enabled) continue
            val value = exclusion.value.lowercase(Locale.ROOT)
            val matched = when (exclusion.type) {
                ExclusionType.FILE_PATH -> path == value.replace('\\', '/')
                ExclusionType.FOLDER_PATH -> {
                    val folder = value.replace('\\', '/').trimEnd('/')
                    path.startsWith("$folder/")
                }
                ExclusionType.EXTENSION ->
                    fileName.substringAfterLast('.', "") == value.removePrefix(".")
                ExclusionType.MIME_TYPE ->
                    candidate.mimeType.lowercase(Locale.ROOT).startsWith(value)
                ExclusionType.PATH_PATTERN -> globMatches(value, path)
                ExclusionType.MAX_SIZE ->
                    value.toLongOrNull()?.let { candidate.sizeBytes > it } == true
                ExclusionType.HIDDEN -> candidate.isHidden
            }
            if (matched) return true
        }
        return false
    }

    /** Glob with `*` (any chars except '/'), `**` (any chars), `?` (one char). */
    private fun globMatches(glob: String, path: String): Boolean {
        val regex = buildString {
            var i = 0
            while (i < glob.length) {
                when (val c = glob[i]) {
                    '*' -> {
                        if (i + 1 < glob.length && glob[i + 1] == '*') {
                            append(".*")
                            i++
                        } else {
                            append("[^/]*")
                        }
                    }
                    '?' -> append("[^/]")
                    else -> append(Regex.escape(c.toString()))
                }
                i++
            }
        }
        return Regex(regex).matches(path)
    }
}
