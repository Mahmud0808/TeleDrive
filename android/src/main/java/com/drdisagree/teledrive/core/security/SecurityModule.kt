package com.drdisagree.teledrive.core.security

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val securityModule = module {
    singleOf(::AppLockManager)
}
