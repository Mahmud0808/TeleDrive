package com.drdisagree.teledrive.domain.usecase

data class ExclusionCandidate(
    val absolutePath: String,
    val sizeBytes: Long,
    val mimeType: String,
    val isHidden: Boolean
)
