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
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.TdlibDatabaseKeyProviderImpl
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.dispatchers.DefaultDispatcherProvider
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.proxy.ProxyProbe
import com.drdisagree.teledrive.core.telegram.DesktopTelegramClient
import com.drdisagree.teledrive.core.telegram.TdlibDatabaseKeyProvider
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramPacer
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.data.repository.ProxyRepositoryImpl
import com.drdisagree.teledrive.data.repository.SettingsRepositoryImpl
import com.drdisagree.teledrive.data.repository.TelegramAuthRepositoryImpl
import com.drdisagree.teledrive.desktop.BuildInfo
import com.drdisagree.teledrive.desktop.crypto.DpapiCredentialCipher
import com.drdisagree.teledrive.desktop.crypto.LocalKeyCredentialCipher
import com.drdisagree.teledrive.desktop.files.DesktopStoragePaths
import com.drdisagree.teledrive.domain.repository.ProxyRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val desktopModule = module {
    singleOf(::DesktopStoragePaths) bind AppStoragePaths::class
    single<CredentialCipher> {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (os.contains("win")) DpapiCredentialCipher() else LocalKeyCredentialCipher(get())
    }
    singleOf(::FileWrappedKeyRepository) bind WrappedKeyRepository::class
    singleOf(::TdlibDatabaseKeyProviderImpl) bind TdlibDatabaseKeyProvider::class
    singleOf(::DefaultDispatcherProvider) bind DispatcherProvider::class
    singleOf(::StreamCrypto)
    singleOf(::PassphraseKdf)
    singleOf(::KeyBackupCodec)
    singleOf(::TelegramPacer)
    singleOf(::ProxyProbe)
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
    single { get<TeleDriveDatabase>().proxyDao() }
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
    singleOf(::ProxyRepositoryImpl) bind ProxyRepository::class
    singleOf(::TelegramAuthRepositoryImpl) bind TelegramAuthRepository::class
}
