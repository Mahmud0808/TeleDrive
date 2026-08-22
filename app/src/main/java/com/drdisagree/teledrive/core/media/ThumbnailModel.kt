package com.drdisagree.teledrive.core.media

/** Coil request model resolved by [ThumbnailFetcher]. */
data class ThumbnailModel(val fileId: String)

fun thumbnailCacheKey(fileId: String): String = "thumb:$fileId"
