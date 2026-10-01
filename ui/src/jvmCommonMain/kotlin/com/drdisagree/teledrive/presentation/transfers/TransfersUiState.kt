package com.drdisagree.teledrive.presentation.transfers

import com.drdisagree.teledrive.domain.model.TransferTask

data class TransfersUiState(
    val active: List<TransferTask> = emptyList(),
    val paused: List<TransferTask> = emptyList(),
    val failed: List<TransferTask> = emptyList(),
    val completed: List<TransferTask> = emptyList(),
    val activeTotal: Int = 0,
    val pausedTotal: Int = 0,
    val failedTotal: Int = 0,
    val completedTotal: Int = 0,
    val loading: Boolean = true
)
