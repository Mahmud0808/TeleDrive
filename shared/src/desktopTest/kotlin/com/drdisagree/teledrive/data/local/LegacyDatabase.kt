package com.drdisagree.teledrive.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.drdisagree.teledrive.data.local.database.MIGRATION_11_12
import com.drdisagree.teledrive.data.local.database.MIGRATION_12_13
import com.drdisagree.teledrive.data.local.database.MIGRATION_13_14
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Builds a database exactly as an older app version left it, from that version's exported schema. */
fun createLegacyDatabase(file: File, version: Int, vararg inserts: String) {
    val schema = Json.parseToJsonElement(schemaFile(version).readText())
        .jsonObject.getValue("database").jsonObject
    BundledSQLiteDriver().open(file.absolutePath).use { connection ->
        schema.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            connection.execSQL(
                entity.jsonObject.getValue("createSql").jsonPrimitive.content
                    .replace("\${TABLE_NAME}", table)
            )
            entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                connection.execSQL(
                    index.jsonObject.getValue("createSql").jsonPrimitive.content
                        .replace("\${TABLE_NAME}", table)
                )
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach {
            connection.execSQL(it.jsonPrimitive.content)
        }
        inserts.forEach { connection.execSQL(it) }
        connection.execSQL("PRAGMA user_version = $version")
    }
}

fun openMigrated(file: File): TeleDriveDatabase =
    Room.databaseBuilder<TeleDriveDatabase>(name = file.absolutePath)
        .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

private fun schemaFile(version: Int): File {
    val relative =
        "schemas/com.drdisagree.teledrive.data.local.database.TeleDriveDatabase/$version.json"
    return listOf(File(relative), File("shared/$relative")).first { it.exists() }
}
