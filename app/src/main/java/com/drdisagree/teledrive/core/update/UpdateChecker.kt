package com.drdisagree.teledrive.core.update

import com.drdisagree.teledrive.BuildConfig
import com.drdisagree.teledrive.core.common.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the latest GitHub release and compares its tag with the running build.
 * Nothing is sent along with the request: it is an anonymous read of a public
 * endpoint, so a check reveals no more than visiting the releases page.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun newerRelease(): AppRelease? = withContext(Dispatchers.IO) {
        val payload = runCatching { fetchLatest() }
            .onFailure { SafeLog.w(TAG, "Update check failed", it) }
            .getOrNull()
            ?: return@withContext null

        val release = runCatching {
            json.decodeFromString(GitHubRelease.serializer(), payload)
        }.getOrNull() ?: return@withContext null

        if (release.draft || release.preRelease) return@withContext null
        val latest = release.tag.removePrefix("v").trim()
        if (!isNewer(latest, BuildConfig.VERSION_NAME)) return@withContext null

        AppRelease(
            version = latest,
            notes = release.body.orEmpty().trim(),
            pageUrl = release.pageUrl
        )
    }

    private fun fetchLatest(): String {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Compares dotted numbers so 1.10 counts as newer than 1.9. */
    private fun isNewer(candidate: String, current: String): Boolean {
        val left = candidate.numbers()
        val right = current.removePrefix("v").numbers()
        if (left.isEmpty()) return false
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun String.numbers(): List<Int> = split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tag: String,
        @SerialName("html_url") val pageUrl: String,
        val body: String? = null,
        val draft: Boolean = false,
        @SerialName("prerelease") val preRelease: Boolean = false
    )

    private companion object {
        const val TAG = "UpdateChecker"
        const val RELEASES_URL =
            "https://api.github.com/repos/Mahmud0808/TeleDrive/releases/latest"
        const val USER_AGENT = "TeleDrive"
        const val TIMEOUT_MS = 15_000
    }
}
