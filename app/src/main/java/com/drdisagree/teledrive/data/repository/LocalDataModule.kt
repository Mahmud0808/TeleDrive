package com.drdisagree.teledrive.data.repository

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val localDataModule = module {
    singleOf(::LocalDataWiper)
}
