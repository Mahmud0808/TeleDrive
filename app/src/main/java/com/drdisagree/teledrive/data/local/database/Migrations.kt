package com.drdisagree.teledrive.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the local step a split transfer reports while it is between parts. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transfers ADD COLUMN stage TEXT")
    }
}

/** Adds the parts a file too large for one Telegram message is split into. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS index_file_parts_fileId ON file_parts(fileId)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_file_parts_remoteUniqueId " +
                    "ON file_parts(remoteUniqueId)"
        )
        db.execSQL("ALTER TABLE files ADD COLUMN partCount INTEGER NOT NULL DEFAULT 0")
    }
}

/** Adds delete tombstones so an interrupted permanent delete can be replayed. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS pending_deletes (" +
                    "chatId INTEGER NOT NULL, " +
                    "messageId INTEGER NOT NULL, " +
                    "fileId TEXT NOT NULL, " +
                    "PRIMARY KEY(chatId, messageId))"
        )
    }
}

/**
 * Adds the publish outbox. Rows start clean: whatever is already in Telegram
 * is what the captions and the folder state document describe.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE files ADD COLUMN pendingPublish INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN pendingPublish INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_files_pendingPublish " +
                    "ON files(pendingPublish)"
        )
    }
}

/**
 * Adds multichannel support. Existing rows all belong to the single drive the
 * app used before, so they are left with a null owner and adopted by the first
 * channel that opens, which keeps a wiped local index in step with the cloud.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE storage_channels " +
                    "ADD COLUMN defaultsSeeded INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE storage_channels " +
                    "ADD COLUMN remoteFileCount INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN chatId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_chatId ON folders(chatId)")

        db.execSQL("ALTER TABLE exclusions ADD COLUMN chatId INTEGER")
        db.execSQL("DROP INDEX IF EXISTS index_exclusions_type_value")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_exclusions_chatId_type_value " +
                    "ON exclusions(chatId, type, value)"
        )

        db.execSQL(
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
