package com.drdisagree.teledrive.desktop

import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.key.Keyer
import coil3.request.Options
import coil3.request.crossfade
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.files.PendingShare
import com.drdisagree.teledrive.core.media.ThumbnailFetcher
import com.drdisagree.teledrive.core.media.ThumbnailModel
import com.drdisagree.teledrive.core.media.ThumbnailStore
import com.drdisagree.teledrive.core.media.thumbnailCacheKey
import com.drdisagree.teledrive.desktop.di.desktopModule
import com.drdisagree.teledrive.desktop.ui.DesktopTheme
import com.drdisagree.teledrive.desktop.ui.ProvideDesktopPlatformActions
import com.drdisagree.teledrive.presentation.TeleDriveApp
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

fun main() {
    SafeLog.verbose = true
    startKoin {
        modules(desktopModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "TeleDrive",
            state = rememberWindowState(width = 1200.dp, height = 800.dp)
        ) {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context)
                    .components {
                        add(ThumbnailFetcher.Factory(getKoin().get<ThumbnailStore>()))
                        add(Keyer<ThumbnailModel> { data, _: Options ->
                            thumbnailCacheKey(data.fileId)
                        })
                    }
                    .crossfade(true)
                    .build()
            }
            DesktopTheme {
                ProvideDesktopPlatformActions {
                    TeleDriveApp(
                        pendingShare = getKoin().get<PendingShare>(),
                        notificationDestination = null,
                        onDestinationHandled = {}
                    )
                }
            }
        }
    }
}
