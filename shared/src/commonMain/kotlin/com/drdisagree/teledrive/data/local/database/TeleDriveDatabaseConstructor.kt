package com.drdisagree.teledrive.data.local.database

import androidx.room.RoomDatabaseConstructor

@Suppress("KotlinNoActualForExpect")
expect object TeleDriveDatabaseConstructor : RoomDatabaseConstructor<TeleDriveDatabase> {
    override fun initialize(): TeleDriveDatabase
}
