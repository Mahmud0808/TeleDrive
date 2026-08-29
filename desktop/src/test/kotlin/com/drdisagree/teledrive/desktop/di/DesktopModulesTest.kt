package com.drdisagree.teledrive.desktop.di

import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class DesktopModulesTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun everyConstructorDependencyResolves() {
        desktopModule.verify()
    }
}
