package com.drdisagree.teledrive.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.desktop.di.desktopModule
import com.drdisagree.teledrive.desktop.ui.AuthScreen
import com.drdisagree.teledrive.desktop.ui.DesktopTheme
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
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
            state = rememberWindowState(width = 960.dp, height = 680.dp)
        ) {
            LaunchedEffect(Unit) {
                getKoin().get<TelegramAuthRepository>().startFromStoredCredentials()
            }
            DesktopTheme {
                AuthScreen()
            }
        }
    }
}
