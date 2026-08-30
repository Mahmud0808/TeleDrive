package com.drdisagree.teledrive.data.repository

/** Removes every local trace of the account: database, keys, caches, files. */
interface LocalDataWiper {

    suspend fun wipe()
}
