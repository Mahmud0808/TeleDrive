package com.drdisagree.teledrive.core.proxy

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val proxyModule = module {
    singleOf(::ProxyFailover)
    singleOf(::ProxyProbe)
}
