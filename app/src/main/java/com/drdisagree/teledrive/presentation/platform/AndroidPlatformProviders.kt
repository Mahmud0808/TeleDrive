package com.drdisagree.teledrive.presentation.platform

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import coil3.compose.AsyncImage
import com.drdisagree.teledrive.BuildConfig
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.files.StandardBackupFolder
import com.drdisagree.teledrive.core.files.DocumentTreePaths
import com.drdisagree.teledrive.core.permissions.manifestPermission
import com.drdisagree.teledrive.core.permissions.openAllFilesAccess
import com.drdisagree.teledrive.core.permissions.openAppSettings
import com.drdisagree.teledrive.presentation.applock.requireDeviceOwner
import com.drdisagree.teledrive.presentation.common.openLink

@Composable
fun ProvidePlatformActions(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val urlOpener = remember(context) { UrlOpener { url -> openLink(context, url) } }

    val folderCallback = remember { CallbackHolder<PickResult>() }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val path = uri?.let { DocumentTreePaths.treeToFilePath(context, it) }
        folderCallback.fire(
            when {
                uri == null -> PickResult.Canceled
                path == null -> PickResult.Unreadable
                else -> PickResult.Picked(path)
            }
        )
    }
    val folderPicker = remember {
        FolderPicker { onPicked ->
            folderCallback.arm(onPicked)
            folderLauncher.launch(null)
        }
    }

    val fileCallback = remember { CallbackHolder<PickResult>() }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val path = uri?.let { DocumentTreePaths.documentToFilePath(context, it) }
        fileCallback.fire(
            when {
                uri == null -> PickResult.Canceled
                path == null -> PickResult.Unreadable
                else -> PickResult.Picked(path)
            }
        )
    }
    val filePicker = remember {
        FilePicker { onPicked ->
            fileCallback.arm(onPicked)
            fileLauncher.launch(arrayOf("*/*"))
        }
    }

    val consentCallback = remember { CallbackHolder<Boolean>() }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        consentCallback.fire(result.resultCode == Activity.RESULT_OK)
    }
    val deleteConsentLauncher = remember {
        DeleteConsentLauncher { request, onResult ->
            consentCallback.arm(onResult)
            consentLauncher.launch(IntentSenderRequest.Builder(request).build())
        }
    }

    val permissionCallback = remember { CallbackHolder<Unit>() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionCallback.fire(Unit) }
    val permissionRequester = remember {
        PermissionRequester { permissions, onDone ->
            val manifest = permissions.mapNotNull { it.manifestPermission }
            if (manifest.isEmpty()) {
                onDone()
            } else {
                permissionCallback.arm { onDone() }
                permissionLauncher.launch(manifest.toTypedArray())
            }
        }
    }

    val systemScreens = remember(context) {
        object : SystemScreens {
            override fun openAppSettings() = openAppSettings(context)
            override fun openAllFilesAccess() = openAllFilesAccess(context)
        }
    }

    val deviceOwnerGate = remember(activity) {
        DeviceOwnerGate { title, subtitle, onDenied, onConfirmed ->
            requireDeviceOwner(
                activity = activity as? FragmentActivity,
                title = title,
                subtitle = subtitle,
                onDenied = onDenied,
                onConfirmed = onConfirmed
            )
        }
    }

    val standardFolders = remember {
        StandardBackupFolder.entries.map { StandardFolderOption(it.labelRes, it.path) }
    }
    val appIcon: @Composable (Modifier) -> Unit = { modifier ->
        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = null,
            modifier = modifier
        )
    }

    CompositionLocalProvider(
        LocalAppIcon provides appIcon,
        LocalStandardFolders provides standardFolders,
        LocalUrlOpener provides urlOpener,
        LocalFolderPicker provides folderPicker,
        LocalFilePicker provides filePicker,
        LocalDeleteConsentLauncher provides deleteConsentLauncher,
        LocalPermissionRequester provides permissionRequester,
        LocalSystemScreens provides systemScreens,
        LocalDeviceOwnerGate provides deviceOwnerGate,
        LocalAppVersion provides BuildConfig.VERSION_NAME,
        content = content
    )
}

private class CallbackHolder<T> {

    private var pending: ((T) -> Unit)? = null

    fun arm(callback: (T) -> Unit) {
        pending = callback
    }

    fun fire(value: T) {
        pending?.invoke(value)
        pending = null
    }
}
