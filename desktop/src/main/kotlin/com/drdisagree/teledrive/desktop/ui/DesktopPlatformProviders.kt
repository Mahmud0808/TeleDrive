package com.drdisagree.teledrive.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.koin.compose.koinInject
import com.drdisagree.teledrive.desktop.files.DesktopFileRevealer
import com.drdisagree.teledrive.desktop.BuildInfo
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import com.drdisagree.teledrive.presentation.platform.DeleteConsentLauncher
import com.drdisagree.teledrive.presentation.platform.DeviceOwnerGate
import com.drdisagree.teledrive.presentation.platform.FilePicker
import com.drdisagree.teledrive.presentation.platform.FileSharer
import com.drdisagree.teledrive.presentation.platform.FolderPicker
import com.drdisagree.teledrive.presentation.platform.LocalAppIcon
import com.drdisagree.teledrive.presentation.platform.LocalAppVersion
import com.drdisagree.teledrive.presentation.platform.LocalDeleteConsentLauncher
import com.drdisagree.teledrive.presentation.platform.LocalDeviceOwnerGate
import com.drdisagree.teledrive.presentation.platform.LocalDownloadLocationConfigurable
import com.drdisagree.teledrive.presentation.platform.LocalFilePicker
import com.drdisagree.teledrive.presentation.platform.LocalFileSharer
import com.drdisagree.teledrive.presentation.platform.LocalFileRevealer
import com.drdisagree.teledrive.presentation.platform.LocalFolderPicker
import com.drdisagree.teledrive.presentation.platform.LocalMultiFilePicker
import com.drdisagree.teledrive.presentation.platform.LocalPermissionRequester
import com.drdisagree.teledrive.presentation.platform.LocalPlatformCapabilities
import com.drdisagree.teledrive.presentation.platform.LocalPlatformScreens
import com.drdisagree.teledrive.presentation.platform.LocalSystemScreens
import com.drdisagree.teledrive.presentation.platform.LocalUrlOpener
import com.drdisagree.teledrive.presentation.platform.PermissionRequester
import com.drdisagree.teledrive.presentation.platform.PlatformCapabilities
import com.drdisagree.teledrive.presentation.platform.MultiFilePicker
import com.drdisagree.teledrive.presentation.platform.PickResult
import com.drdisagree.teledrive.presentation.platform.SystemScreens
import com.drdisagree.teledrive.presentation.platform.UrlOpener
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.ic_launcher
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import kotlin.concurrent.thread

@Composable
fun ProvideDesktopPlatformActions(content: @Composable () -> Unit) {
    val capabilities = koinInject<PlatformCapabilities>()
    val urlOpener = remember {
        UrlOpener { url ->
            runCatching { Desktop.getDesktop().browse(URI(url)) }
        }
    }
    val folderPicker = remember {
        FolderPicker { onPicked ->
            thread {
                val chooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                }
                val result = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    PickResult.Picked(chooser.selectedFile.absolutePath)
                } else {
                    PickResult.Canceled
                }
                onPicked(result)
            }
        }
    }
    val filePicker = remember {
        FilePicker { onPicked ->
            thread {
                val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD)
                dialog.isVisible = true
                val file = dialog.file
                val result = if (file == null) {
                    PickResult.Canceled
                } else {
                    PickResult.Picked(File(dialog.directory, file).absolutePath)
                }
                onPicked(result)
            }
        }
    }
    val multiFilePicker = remember {
        MultiFilePicker { onPicked ->
            thread {
                val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD)
                dialog.isMultipleMode = true
                dialog.isVisible = true
                onPicked(dialog.files.orEmpty().map { it.absolutePath })
            }
        }
    }
    val fileSharer = remember {
        FileSharer { paths, _, _ ->
            paths.firstOrNull()?.let { path ->
                runCatching {
                    Desktop.getDesktop().open(File(path).parentFile)
                }
            }
        }
    }
    val deleteConsentLauncher = remember {
        DeleteConsentLauncher { _, onResult -> onResult(true) }
    }
    val fileRevealer = remember { DesktopFileRevealer() }
    val permissionRequester = remember {
        PermissionRequester { _, onDone -> onDone() }
    }
    val systemScreens = remember {
        object : SystemScreens {
            override fun openAppSettings() = Unit
            override fun openAllFilesAccess() = Unit
        }
    }
    val deviceOwnerGate = remember {
        DeviceOwnerGate { _, _, _, onConfirmed -> onConfirmed() }
    }

    val appIcon: @Composable (Modifier) -> Unit = { modifier ->
        Image(
            painter = painterResource(Res.drawable.ic_launcher),
            contentDescription = null,
            modifier = modifier
        )
    }

    CompositionLocalProvider(
        LocalAppIcon provides appIcon,
        LocalUrlOpener provides urlOpener,
        LocalFolderPicker provides folderPicker,
        LocalFilePicker provides filePicker,
        LocalMultiFilePicker provides multiFilePicker,
        LocalFileSharer provides fileSharer,
        LocalDeleteConsentLauncher provides deleteConsentLauncher,
        LocalPermissionRequester provides permissionRequester,
        LocalSystemScreens provides systemScreens,
        LocalDeviceOwnerGate provides deviceOwnerGate,
        LocalAppVersion provides BuildInfo.VERSION,
        LocalDownloadLocationConfigurable provides true,
        LocalPlatformScreens provides DesktopPlatformScreens,
        LocalFileRevealer provides fileRevealer,
        LocalPlatformCapabilities provides capabilities,
        content = content
    )
}
