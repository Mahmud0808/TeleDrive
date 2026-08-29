package com.drdisagree.teledrive.core.telegram

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val telegramModule = module {
    singleOf(::TdLibTelegramClient) bind TelegramClient::class
    singleOf(::TelegramPacer)
}
