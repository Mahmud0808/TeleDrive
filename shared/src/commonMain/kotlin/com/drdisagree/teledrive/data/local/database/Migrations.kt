package com.drdisagree.teledrive.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `folder_tombstones` (`id` TEXT NOT NULL, " +
                    "`chatId` INTEGER, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

/** modifiedAt only moves on a rename or a move, so it cannot order flag and trash changes. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE folders ADD COLUMN changedAt INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE folders SET changedAt = modifiedAt")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE files ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE folders ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE files ADD COLUMN iconFileId TEXT")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS proxies (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "label TEXT NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "host TEXT NOT NULL, " +
                    "port INTEGER NOT NULL, " +
                    "username TEXT, " +
                    "password TEXT, " +
                    "secret TEXT, " +
                    "addedAt INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE transfers ADD COLUMN stage TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS file_parts (" +
                    "fileId TEXT NOT NULL, " +
                    "partIndex INTEGER NOT NULL, " +
                    "chatId INTEGER, " +
                    "messageId INTEGER, " +
                    "remoteFileId TEXT, " +
                    "remoteUniqueId TEXT, " +
                    "plainOffset INTEGER NOT NULL, " +
                    "plainSize INTEGER NOT NULL, " +
                    "storedSize INTEGER NOT NULL, " +
                    "uploadedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(fileId, partIndex))"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_file_parts_fileId ON file_parts(fileId)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_file_parts_remoteUniqueId " +
                    "ON file_parts(remoteUniqueId)"
        )
        connection.execSQL("ALTER TABLE files ADD COLUMN partCount INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS pending_deletes (" +
                    "chatId INTEGER NOT NULL, " +
                    "messageId INTEGER NOT NULL, " +
                    "fileId TEXT NOT NULL, " +
                    "PRIMARY KEY(chatId, messageId))"
        )
    }
}

/** Rows start clean: the captions and folder document already describe what is in Telegram. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE files ADD COLUMN pendingPublish INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE folders ADD COLUMN pendingPublish INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_files_pendingPublish " +
                    "ON files(pendingPublish)"
        )
    }
}

/**
 * Existing rows get no owner and are adopted by the first channel that opens, keeping a wiped index
 * in step.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE storage_channels " +
                    "ADD COLUMN defaultsSeeded INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE storage_channels " +
                    "ADD COLUMN remoteFileCount INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE folders ADD COLUMN chatId INTEGER")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_folders_chatId ON folders(chatId)")

        connection.execSQL("ALTER TABLE exclusions ADD COLUMN chatId INTEGER")
        connection.execSQL("DROP INDEX IF EXISTS index_exclusions_type_value")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_exclusions_chatId_type_value " +
                    "ON exclusions(chatId, type, value)"
        )

        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS storage_channels (
                   chatId INTEGER NOT NULL PRIMARY KEY,
                   title TEXT NOT NULL,
                   backupFolders TEXT NOT NULL DEFAULT '',
                   photoPath TEXT,
                   defaultsSeeded INTEGER NOT NULL DEFAULT 0,
                   remoteFileCount INTEGER NOT NULL DEFAULT 0,
                   addedAt INTEGER NOT NULL,
                   lastOpenedAt INTEGER NOT NULL
               )"""
        )
    }
}
