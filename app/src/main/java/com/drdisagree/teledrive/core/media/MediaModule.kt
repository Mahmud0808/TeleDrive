package com.drdisagree.teledrive.core.media

import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.key.Keyer
import coil3.request.Options
import coil3.request.crossfade
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

@UnstableApi
val mediaModule = module {
    single {
        ImageLoader.Builder(androidContext())
            .components {
                add(ThumbnailFetcher.Factory(get<ThumbnailStore>()))
                add(Keyer<ThumbnailModel> { data, _: Options -> thumbnailCacheKey(data.fileId) })
            }
            .crossfade(true)
            .build()
    }
    singleOf(::ThumbnailStore)
    singleOf(::MediaMetadataExtractor)
    factoryOf(::TelegramDataSourceFactory)
}
