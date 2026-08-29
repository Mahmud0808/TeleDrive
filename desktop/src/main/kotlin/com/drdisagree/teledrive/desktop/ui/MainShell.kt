package com.drdisagree.teledrive.desktop.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.drdisagree.teledrive.desktop.resources.Res
import com.drdisagree.teledrive.desktop.resources.nav_channels
import com.drdisagree.teledrive.desktop.resources.nav_files
import com.drdisagree.teledrive.desktop.resources.nav_gallery
import com.drdisagree.teledrive.desktop.resources.nav_proxy
import com.drdisagree.teledrive.desktop.resources.nav_transfers
import com.drdisagree.teledrive.desktop.resources.nav_trash
import com.drdisagree.teledrive.presentation.channels.ChannelsScreen
import com.drdisagree.teledrive.presentation.gallery.GalleryScreen
import com.drdisagree.teledrive.presentation.proxy.ProxyScreen
import com.drdisagree.teledrive.presentation.transfers.TransfersScreen
import com.drdisagree.teledrive.presentation.trash.TrashScreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class ShellDestination(
    val label: StringResource,
    val icon: ImageVector
) {
    FILES(Res.string.nav_files, Icons.Outlined.Folder),
    GALLERY(Res.string.nav_gallery, Icons.Outlined.Image),
    TRANSFERS(Res.string.nav_transfers, Icons.Outlined.SwapVert),
    TRASH(Res.string.nav_trash, Icons.Outlined.Delete),
    CHANNELS(Res.string.nav_channels, Icons.Outlined.Cloud),
    PROXY(Res.string.nav_proxy, Icons.Outlined.Lan)
}

@Composable
fun MainShell() {
    var destination by remember { mutableStateOf(ShellDestination.FILES) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                ShellDestination.entries.forEach { entry ->
                    NavigationRailItem(
                        selected = destination == entry,
                        onClick = { destination = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) }
                    )
                }
            }
            when (destination) {
                ShellDestination.FILES -> DriveScreen()
                ShellDestination.GALLERY -> GalleryScreen(onOpenFile = { _, _ -> })
                ShellDestination.TRANSFERS -> TransfersScreen(onBack = {})
                ShellDestination.TRASH -> TrashScreen(onBack = {})
                ShellDestination.CHANNELS -> ChannelsScreen(onBack = {})
                ShellDestination.PROXY -> ProxyScreen(onBack = {})
            }
        }
    }
}
