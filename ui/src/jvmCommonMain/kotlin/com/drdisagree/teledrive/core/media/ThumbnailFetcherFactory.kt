package com.drdisagree.teledrive.core.media

import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.request.Options

class ThumbnailFetcherFactory(
    private val thumbnailStore: ThumbnailStore
) : Fetcher.Factory<ThumbnailModel> {
    override fun create(
        data: ThumbnailModel,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher = ThumbnailFetcher(data, options, thumbnailStore)
}
