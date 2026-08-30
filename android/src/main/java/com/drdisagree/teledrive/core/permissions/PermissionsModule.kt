package com.drdisagree.teledrive.core.permissions

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val permissionsModule = module {
    singleOf(::AndroidPermissionChecker) bind PermissionChecker::class
}
