package com.drdisagree.teledrive.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val pageUrl: String,
    val body: String? = null,
    val draft: Boolean = false,
    @SerialName("prerelease") val preRelease: Boolean = false
)
