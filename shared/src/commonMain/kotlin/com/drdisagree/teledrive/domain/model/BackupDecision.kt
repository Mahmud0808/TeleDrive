package com.drdisagree.teledrive.domain.model

enum class BackupDecision {
    BACKUP,
    SKIP_UNCHANGED,
    SKIP_EXCLUDED,
    SKIP_TOO_LARGE,
    SKIP_DUPLICATE
}
