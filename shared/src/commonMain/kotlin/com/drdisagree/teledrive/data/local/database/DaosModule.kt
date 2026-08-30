package com.drdisagree.teledrive.data.local.database

import org.koin.dsl.module

val daosModule = module {
    single { get<TeleDriveDatabase>().storageChannelDao() }
    single { get<TeleDriveDatabase>().fileDao() }
    single { get<TeleDriveDatabase>().folderDao() }
    single { get<TeleDriveDatabase>().transferDao() }
    single { get<TeleDriveDatabase>().backupDao() }
    single { get<TeleDriveDatabase>().exclusionDao() }
    single { get<TeleDriveDatabase>().thumbnailDao() }
    single { get<TeleDriveDatabase>().cacheDao() }
    single { get<TeleDriveDatabase>().pendingDeleteDao() }
    single { get<TeleDriveDatabase>().filePartDao() }
    single { get<TeleDriveDatabase>().proxyDao() }
}
