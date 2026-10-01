package com.drdisagree.teledrive.core.telegram

interface TdlibDatabaseKeyProvider {
    fun databaseKey(): ByteArray
}
