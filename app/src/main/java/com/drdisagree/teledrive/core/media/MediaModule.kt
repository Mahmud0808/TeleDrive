package com.drdisagree.teledrive.core.media

import android.content.Context
import coil3.ImageLoader
import coil3.key.Keyer
import coil3.request.crossfade
import coil3.request.Options
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        thumbnailStore: ThumbnailStore
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(ThumbnailFetcher.Factory(thumbnailStore))
            add(Keyer<ThumbnailModel> { data, _: Options -> "thumb:${data.fileId}" })
        }
        .crossfade(true)
        .build()
}
