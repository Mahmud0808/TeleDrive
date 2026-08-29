package com.drdisagree.teledrive.domain.model

enum class TransferState {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED

    val isActive: Boolean
        get() = this == QUEUED || this == RUNNING
}
