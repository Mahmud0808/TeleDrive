package com.drdisagree.teledrive.domain.model

data class LinkMetadata(
    val url: String,
    val siteName: String?,
    val title: String?,
    val description: String?,
    val imagePath: String?
)
