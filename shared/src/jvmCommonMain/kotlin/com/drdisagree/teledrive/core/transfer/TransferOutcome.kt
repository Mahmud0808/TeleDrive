package com.drdisagree.teledrive.core.transfer

sealed interface TransferOutcome {
    data object Completed : TransferOutcome
    data object Paused : TransferOutcome
    data object Canceled : TransferOutcome
    data class Failed(val message: String, val retryAfterSeconds: Int? = null) : TransferOutcome
}
