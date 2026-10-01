package com.drdisagree.teledrive.presentation.preview

internal data class PdfLink(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val uri: String?,
    val targetPage: Int?
)
