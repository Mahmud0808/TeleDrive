package com.drdisagree.teledrive.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class AppModulesTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun everyConstructorDependencyResolves() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(
                Context::class,
                WorkerParameters::class,
                SavedStateHandle::class
            )
        )
    }
}
