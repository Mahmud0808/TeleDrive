package com.drdisagree.teledrive.core.media

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

class ThumbnailFetcher(
    private val model: ThumbnailModel,
    private val options: Options,
    private val thumbnailStore: ThumbnailStore
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = thumbnailStore.thumbnailBytes(model.fileId) ?: return null
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(bytes),
                fileSystem = FileSystem.SYSTEM
            ),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    }

    class Factory(
        private val thumbnailStore: ThumbnailStore
    ) : Fetcher.Factory<ThumbnailModel> {
        override fun create(
            data: ThumbnailModel,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = ThumbnailFetcher(data, options, thumbnailStore)
    }
}
