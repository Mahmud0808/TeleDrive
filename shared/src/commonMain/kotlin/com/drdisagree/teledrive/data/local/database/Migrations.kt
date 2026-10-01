package com.drdisagree.teledrive.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Rebuilds the tables since SQLite has RENAME COLUMN only from 3.25 (API 30). Room turns
 * foreign keys on after migrating, so dropping the old tables cascades nothing.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `folders_new` (`id` TEXT NOT NULL, `chatId` " +
                    "INTEGER, `parentId` TEXT, `name` TEXT NOT NULL, `isHidden` INTEGER NOT " +
                    "NULL, `isArchived` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, " +
                    "`isAvailableOffline` INTEGER NOT NULL, `trashedAt` INTEGER, " +
                    "`preTrashParentId` TEXT, `pendingPublish` INTEGER NOT NULL, `createdAt` " +
                    "INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, `changedAt` INTEGER NOT " +
                    "NULL, PRIMARY KEY(`id`), FOREIGN KEY(`parentId`) REFERENCES " +
                    "`folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "INSERT INTO `folders_new` (`id`, `chatId`, `parentId`, `name`, " +
                    "`isHidden`, `isArchived`, `isFavorite`, `isAvailableOffline`, " +
                    "`trashedAt`, `preTrashParentId`, `pendingPublish`, `createdAt`, " +
                    "`modifiedAt`, `changedAt`) SELECT `id`, `chatId`, `parentId`, `name`, " +
                    "`isHidden`, `isArchived`, `isFavorite`, `isPinned`, `trashedAt`, " +
                    "`preTrashParentId`, `pendingPublish`, `createdAt`, `modifiedAt`, " +
                    "`changedAt` FROM `folders`"
        )
        connection.execSQL("DROP TABLE `folders`")
        connection.execSQL("ALTER TABLE `folders_new` RENAME TO `folders`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` " +
                    "(`parentId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_chatId` ON `folders` " +
                    "(`chatId`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `files_new` (`id` TEXT NOT NULL, `folderId` " +
                    "TEXT, `name` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `mimeType` " +
                    "TEXT NOT NULL, `category` TEXT NOT NULL, `localPath` TEXT, `contentHash` " +
                    "TEXT, `chatId` INTEGER, `messageId` INTEGER, `remoteFileId` TEXT, " +
                    "`remoteUniqueId` TEXT, `backupState` TEXT NOT NULL, `isHidden` INTEGER " +
                    "NOT NULL, `isArchived` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, " +
                    "`isAvailableOffline` INTEGER NOT NULL, `isEncrypted` INTEGER NOT NULL, " +
                    "`width` INTEGER, `height` INTEGER, `durationMs` INTEGER, `trashedAt` " +
                    "INTEGER, `preTrashFolderId` TEXT, `pendingPublish` INTEGER NOT NULL, " +
                    "`partCount` INTEGER NOT NULL, `iconFileId` TEXT, `createdAt` INTEGER NOT " +
                    "NULL, `modifiedAt` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY " +
                    "KEY(`id`), FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE " +
                    "NO ACTION ON DELETE SET NULL )"
        )
        connection.execSQL(
            "INSERT INTO `files_new` (`id`, `folderId`, `name`, `sizeBytes`, " +
                    "`mimeType`, `category`, `localPath`, `contentHash`, `chatId`, " +
                    "`messageId`, `remoteFileId`, `remoteUniqueId`, `backupState`, " +
                    "`isHidden`, `isArchived`, `isFavorite`, `isAvailableOffline`, " +
                    "`isEncrypted`, `width`, `height`, `durationMs`, `trashedAt`, " +
                    "`preTrashFolderId`, `pendingPublish`, `partCount`, `iconFileId`, " +
                    "`createdAt`, `modifiedAt`, `addedAt`) SELECT `id`, `folderId`, `name`, " +
                    "`sizeBytes`, `mimeType`, `category`, `localPath`, `contentHash`, " +
                    "`chatId`, `messageId`, `remoteFileId`, `remoteUniqueId`, `backupState`, " +
                    "`isHidden`, `isArchived`, `isFavorite`, `isPinned`, `isEncrypted`, " +
                    "`width`, `height`, `durationMs`, `trashedAt`, `preTrashFolderId`, " +
                    "`pendingPublish`, `partCount`, `iconFileId`, `createdAt`, `modifiedAt`, " +
                    "`addedAt` FROM `files`"
        )
        connection.execSQL("DROP TABLE `files`")
        connection.execSQL("ALTER TABLE `files_new` RENAME TO `files`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_folderId` ON `files` " +
                    "(`folderId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_name` ON `files` (`name`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_category` ON `files` " +
                    "(`category`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_backupState` ON `files` " +
                    "(`backupState`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_trashedAt` ON `files` " +
                    "(`trashedAt`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_remoteUniqueId` ON `files` " +
                    "(`remoteUniqueId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_localPath` ON `files` " +
                    "(`localPath`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_contentHash` ON `files` " +
                    "(`contentHash`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_files_pendingPublish` ON `files` " +
                    "(`pendingPublish`)"
        )
    }
}

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
