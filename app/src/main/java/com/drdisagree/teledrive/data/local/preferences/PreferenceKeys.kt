package com.drdisagree.teledrive.data.local.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object PreferenceKeys {
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val STORAGE_CHAT_ID = longPreferencesKey("storage_chat_id")
    val API_ID_ENCRYPTED = stringPreferencesKey("api_id_enc")
    val API_HASH_ENCRYPTED = stringPreferencesKey("api_hash_enc")

    val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    val INSTANT_BACKUP_ENABLED = booleanPreferencesKey("instant_backup_enabled")
    val BACKUP_FOLDERS = stringSetPreferencesKey("backup_folders")
    val BACKUP_WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
    val BACKUP_CHARGING_ONLY = booleanPreferencesKey("backup_charging_only")
    val BACKUP_INTERVAL_HOURS = intPreferencesKey("backup_interval_hours")
    val BACKUP_MAX_FILE_SIZE_MB = intPreferencesKey("backup_max_file_size_mb")

    val MAX_CACHE_SIZE_MB = intPreferencesKey("max_cache_size_mb")
    val TRASH_AUTO_CLEAR_DAYS = intPreferencesKey("trash_auto_clear_days")

    val BLOCK_SCREEN_CAPTURE = booleanPreferencesKey("block_screen_capture")
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val AUTO_LOCK_TIMEOUT_MINUTES = intPreferencesKey("auto_lock_timeout_minutes")
    val ENCRYPT_FILES = booleanPreferencesKey("encrypt_files")
    val ENCRYPT_THUMBNAILS = booleanPreferencesKey("encrypt_thumbnails")
    val KEY_BACKUP_CREATED = booleanPreferencesKey("key_backup_created")

    val THEME = stringPreferencesKey("theme")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val VIEW_MODE = stringPreferencesKey("view_mode")
    val GRID_SIZE = intPreferencesKey("grid_size")
    val ALBUM_GRID_SIZE = intPreferencesKey("album_grid_size")
    val LAYOUT_DENSITY = stringPreferencesKey("layout_density")
    val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
    val SHOW_ARCHIVED_FILES = booleanPreferencesKey("show_archived_files")
    val SHOW_RECENT_FILES = booleanPreferencesKey("show_recent_files")
    val LINK_PREVIEWS = booleanPreferencesKey("link_previews")
    val UPDATE_CHECK_ENABLED = booleanPreferencesKey("update_check_enabled")
    val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    val TEXT_PREVIEW_SCALE = floatPreferencesKey("text_preview_scale")

    val SORT_FIELD = stringPreferencesKey("sort_field")
    val SORT_DIRECTION = stringPreferencesKey("sort_direction")

    val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
    val STREAM_BEFORE_DOWNLOAD = booleanPreferencesKey("stream_before_download")
    val BACKUP_NOTIFICATIONS = booleanPreferencesKey("backup_notifications")
    val FAILURE_NOTIFICATIONS = booleanPreferencesKey("failure_notifications")

    val TRANSFER_CONCURRENCY = intPreferencesKey("transfer_concurrency")
    val TRANSFER_RETRY_COUNT = intPreferencesKey("transfer_retry_count")
    val ALLOW_METERED_TRANSFERS = booleanPreferencesKey("allow_metered_transfers")
    val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
}
