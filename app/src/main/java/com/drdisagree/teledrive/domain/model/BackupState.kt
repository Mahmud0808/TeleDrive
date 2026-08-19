package com.drdisagree.teledrive.domain.model

enum class BackupState {
    NONE,
    QUEUED,
    UPLOADING,
    BACKED_UP,
    FAILED
}
