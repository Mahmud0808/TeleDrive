package com.drdisagree.teledrive.domain.model

data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val storageChatId: Long? = null,

    val autoBackupEnabled: Boolean = false,
    val instantBackupEnabled: Boolean = true,
    val backupFolders: Set<String> = emptySet(),
    val backupWifiOnly: Boolean = false,
    val backupChargingOnly: Boolean = false,
    val backupIntervalHours: Int = 24,
    val backupMaxFileSizeMb: Int = 0,

    val maxCacheSizeMb: Int = 1024,
    val trashAutoClearDays: Int = 30,

    val appLockEnabled: Boolean = false,
    val blockScreenCapture: Boolean = false,
    val autoLockTimeoutMinutes: Int = 5,
    val encryptFiles: Boolean = false,
    val encryptThumbnails: Boolean = false,
    val keyBackupCreated: Boolean = false,

    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColor: Boolean = true,
    val viewMode: ViewMode = ViewMode.GRID,
    val gridSize: Int = 3,
    val albumGridSize: Int = 3,
    val layoutDensity: LayoutDensity = LayoutDensity.COMFORTABLE,
    val showHiddenFiles: Boolean = false,
    val showArchivedFiles: Boolean = true,
    val showRecentFiles: Boolean = true,
    val linkPreviews: Boolean = true,
    val textPreviewScale: Float = 1f,

    val sortField: FileSortField = FileSortField.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,

    val backgroundPlayback: Boolean = false,
    val streamBeforeDownload: Boolean = true,

    val backupNotifications: Boolean = true,
    val failureNotifications: Boolean = true,

    val transferConcurrency: Int = 2,
    val transferRetryCount: Int = 3,
    val allowMeteredTransfers: Boolean = true,
    val debugLogging: Boolean = false,

    val updateCheckEnabled: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
    val notifiedUpdateVersion: String = "",
    val skippedUpdateVersion: String = ""
)
