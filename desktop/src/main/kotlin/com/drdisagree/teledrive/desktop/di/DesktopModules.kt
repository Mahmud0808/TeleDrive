package com.drdisagree.teledrive.desktop.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drdisagree.teledrive.core.crypto.CredentialCipher
import com.drdisagree.teledrive.core.crypto.FileWrappedKeyRepository
import com.drdisagree.teledrive.core.crypto.KeyBackupCodec
import com.drdisagree.teledrive.core.crypto.PassphraseKdf
import com.drdisagree.teledrive.core.crypto.SecureFileDeleter
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.TdlibDatabaseKeyProviderImpl
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.dispatchers.DefaultDispatcherProvider
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.DownloadWriter
import com.drdisagree.teledrive.core.files.FileImporter
import com.drdisagree.teledrive.core.files.PendingShare
import com.drdisagree.teledrive.core.files.LocalCopyDeleter
import com.drdisagree.teledrive.core.files.sharedFilesModule
import com.drdisagree.teledrive.core.media.MediaMetadataExtractor
import com.drdisagree.teledrive.core.media.ThumbnailMemoryCache
import com.drdisagree.teledrive.core.media.ThumbnailStore
import com.drdisagree.teledrive.core.network.NetworkMonitor
import com.drdisagree.teledrive.core.permissions.PermissionChecker
import com.drdisagree.teledrive.core.proxy.ProxyProbe
import com.drdisagree.teledrive.core.security.AppLockManager
import com.drdisagree.teledrive.core.publish.PublishOutboxDrainer
import com.drdisagree.teledrive.core.publish.PublishScheduler
import com.drdisagree.teledrive.core.telegram.DesktopTelegramClient
import com.drdisagree.teledrive.core.telegram.TdlibDatabaseKeyProvider
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramPacer
import com.drdisagree.teledrive.core.transfer.BackupSessionTracker
import com.drdisagree.teledrive.core.transfer.CountingBackupSessionTracker
import com.drdisagree.teledrive.core.transfer.MaintenanceScheduler
import com.drdisagree.teledrive.core.transfer.TransferErrorMessages
import com.drdisagree.teledrive.core.transfer.TransferScheduler
import com.drdisagree.teledrive.core.transfer.transferEngineModule
import com.drdisagree.teledrive.core.update.UpdateChecker
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.data.local.database.daosModule
import com.drdisagree.teledrive.data.repository.LocalDataWiper
import com.drdisagree.teledrive.data.repository.repositoryModule
import com.drdisagree.teledrive.desktop.BuildInfo
import com.drdisagree.teledrive.desktop.DesktopPlatformCapabilities
import com.drdisagree.teledrive.desktop.crypto.DpapiCredentialCipher
import com.drdisagree.teledrive.desktop.data.DesktopLocalDataWiper
import com.drdisagree.teledrive.desktop.crypto.LocalKeyCredentialCipher
import com.drdisagree.teledrive.desktop.files.DesktopDownloadWriter
import com.drdisagree.teledrive.desktop.files.DesktopFileImporter
import com.drdisagree.teledrive.desktop.files.DesktopPendingShare
import com.drdisagree.teledrive.desktop.files.DesktopLocalCopyDeleter
import com.drdisagree.teledrive.desktop.files.DesktopStandardFolderPaths
import com.drdisagree.teledrive.desktop.files.DesktopStoragePaths
import com.drdisagree.teledrive.desktop.media.DesktopMediaMetadataExtractor
import com.drdisagree.teledrive.desktop.media.DesktopThumbnailStore
import com.drdisagree.teledrive.desktop.media.NoopThumbnailMemoryCache
import com.drdisagree.teledrive.desktop.network.DesktopNetworkMonitor
import com.drdisagree.teledrive.desktop.permissions.DesktopPermissionChecker
import com.drdisagree.teledrive.desktop.publish.DesktopPublishScheduler
import com.drdisagree.teledrive.desktop.transfer.DesktopTransferErrorMessages
import com.drdisagree.teledrive.desktop.transfer.DesktopMaintenanceScheduler
import com.drdisagree.teledrive.desktop.transfer.DesktopTransferScheduler
import com.drdisagree.teledrive.domain.usecase.useCaseModule
import com.drdisagree.teledrive.presentation.di.sharedUiModule
import com.drdisagree.teledrive.presentation.platform.PlatformCapabilities
import com.drdisagree.teledrive.presentation.platform.StandardFolderPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val desktopModule = module {
    includes(daosModule, repositoryModule, useCaseModule, sharedFilesModule, transferEngineModule, sharedUiModule)

    singleOf(::DesktopStoragePaths) bind AppStoragePaths::class
    single<CredentialCipher> {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (os.contains("win")) DpapiCredentialCipher() else LocalKeyCredentialCipher(get())
    }
    singleOf(::FileWrappedKeyRepository) bind WrappedKeyRepository::class
    singleOf(::TdlibDatabaseKeyProviderImpl) bind TdlibDatabaseKeyProvider::class
    singleOf(::DefaultDispatcherProvider) bind DispatcherProvider::class
    singleOf(::StreamCrypto)
    singleOf(::SecureFileDeleter)
    singleOf(::PassphraseKdf)
    singleOf(::KeyBackupCodec)
    singleOf(::TelegramPacer)
    singleOf(::ProxyProbe)
    singleOf(::AppLockManager)
    single { UpdateChecker(BuildInfo.VERSION) }
    singleOf(::DesktopPermissionChecker) bind PermissionChecker::class
    singleOf(::DesktopStandardFolderPaths) bind StandardFolderPaths::class
    singleOf(::DesktopPlatformCapabilities) bind PlatformCapabilities::class
    singleOf(::PublishOutboxDrainer)
    singleOf(::DesktopNetworkMonitor) bind NetworkMonitor::class
    singleOf(::DesktopTransferErrorMessages) bind TransferErrorMessages::class
    singleOf(::DesktopDownloadWriter) bind DownloadWriter::class
    singleOf(::DesktopFileImporter) bind FileImporter::class
    singleOf(::DesktopPendingShare) bind PendingShare::class
    singleOf(::DesktopLocalCopyDeleter) bind LocalCopyDeleter::class
    singleOf(::DesktopMediaMetadataExtractor) bind MediaMetadataExtractor::class
    singleOf(::DesktopThumbnailStore) bind ThumbnailStore::class
    singleOf(::NoopThumbnailMemoryCache) bind ThumbnailMemoryCache::class
    singleOf(::CountingBackupSessionTracker) bind BackupSessionTracker::class
    singleOf(::DesktopTransferScheduler) bind TransferScheduler::class
    singleOf(::DesktopMaintenanceScheduler) bind MaintenanceScheduler::class
    singleOf(::DesktopLocalDataWiper) bind LocalDataWiper::class
    singleOf(::DesktopPublishScheduler) bind PublishScheduler::class
    single<TelegramClient> {
        DesktopTelegramClient(get(), get(), get(), BuildInfo.VERSION)
    }
    single<DataStore<Preferences>> {
        val storagePaths = get<AppStoragePaths>()
        PreferenceDataStoreFactory.createWithPath {
            File(File(storagePaths.filesDir, "datastore"), "settings.preferences_pb")
                .apply { parentFile?.mkdirs() }
                .toOkioPath()
        }
    }
    single<TeleDriveDatabase> {
        val storagePaths = get<AppStoragePaths>()
        Room.databaseBuilder<TeleDriveDatabase>(
            name = File(storagePaths.filesDir, "teledrive.db").absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
