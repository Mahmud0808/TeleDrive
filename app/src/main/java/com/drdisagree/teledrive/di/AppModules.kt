package com.drdisagree.teledrive.di

import com.drdisagree.teledrive.core.common.commonModule
import com.drdisagree.teledrive.core.crypto.cryptoModule
import com.drdisagree.teledrive.core.dispatchers.dispatchersModule
import com.drdisagree.teledrive.core.files.filesModule
import com.drdisagree.teledrive.core.media.mediaModule
import com.drdisagree.teledrive.core.network.networkModule
import com.drdisagree.teledrive.core.permissions.permissionsModule
import com.drdisagree.teledrive.core.proxy.proxyModule
import com.drdisagree.teledrive.core.publish.publishModule
import com.drdisagree.teledrive.core.security.securityModule
import com.drdisagree.teledrive.core.telegram.telegramModule
import com.drdisagree.teledrive.core.transfer.transferModule
import com.drdisagree.teledrive.core.update.updateModule
import com.drdisagree.teledrive.data.local.database.databaseModule
import com.drdisagree.teledrive.data.local.preferences.preferencesModule
import com.drdisagree.teledrive.data.repository.repositoryModule
import com.drdisagree.teledrive.domain.usecase.useCaseModule
import com.drdisagree.teledrive.presentation.presentationModule

val appModules = listOf(
    commonModule,
    cryptoModule,
    dispatchersModule,
    filesModule,
    mediaModule,
    networkModule,
    permissionsModule,
    proxyModule,
    publishModule,
    securityModule,
    telegramModule,
    transferModule,
    updateModule,
    databaseModule,
    preferencesModule,
    repositoryModule,
    useCaseModule,
    presentationModule
)
