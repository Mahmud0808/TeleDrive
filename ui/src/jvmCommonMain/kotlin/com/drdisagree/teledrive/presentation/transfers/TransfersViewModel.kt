package com.drdisagree.teledrive.presentation.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.domain.model.TransferSection
import com.drdisagree.teledrive.domain.model.TransferTask
import com.drdisagree.teledrive.domain.repository.TransferRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

class TransfersViewModel(
    private val transferRepository: TransferRepository
) : ViewModel() {

    private val rows = combine(
        transferRepository.observeSection(TransferSection.ACTIVE, SECTION_LIMIT),
        transferRepository.observeSection(TransferSection.PAUSED, SECTION_LIMIT),
        transferRepository.observeSection(TransferSection.FAILED, SECTION_LIMIT),
        transferRepository.observeSection(TransferSection.COMPLETED, SECTION_LIMIT)
    ) { active, paused, failed, completed ->
        listOf(
            active.sortedWith(TRANSFER_ORDER),
            paused.sortedWith(TRANSFER_ORDER),
            failed.sortedWith(TRANSFER_ORDER),
            completed.sortedWith(FINISHED_ORDER)
        )
    }

    private val totals = combine(
        transferRepository.observeSectionCount(TransferSection.ACTIVE),
        transferRepository.observeSectionCount(TransferSection.PAUSED),
        transferRepository.observeSectionCount(TransferSection.FAILED),
        transferRepository.observeSectionCount(TransferSection.COMPLETED)
    ) { counts -> counts.toList() }

    val uiState: StateFlow<TransfersUiState> = combine(rows, totals) { sections, counts ->
        TransfersUiState(
            active = sections[0],
            paused = sections[1],
            failed = sections[2],
            completed = sections[3],
            activeTotal = counts[0],
            pausedTotal = counts[1],
            failedTotal = counts[2],
            completedTotal = counts[3],
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransfersUiState())

    fun pause(id: String) = launch { transferRepository.pause(id) }

    fun resume(id: String) = launch { transferRepository.resume(id) }

    fun cancel(id: String) = launch { transferRepository.cancel(id) }

    fun retry(id: String) = launch { transferRepository.retry(id) }

    fun pauseAll() = launch { transferRepository.pauseAll() }

    fun resumeAll() = launch { transferRepository.resumeAll() }

    fun cancelAll() = launch { transferRepository.cancelAll() }

    fun clearFinished() = launch { transferRepository.clearFinished() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        /**
         * A backup can queue tens of thousands of rows. Reading them all into
         * one cursor overflows its window while the workers are still writing
         * progress into the same table, so each section is capped.
         */
        const val SECTION_LIMIT = 200

        /**
         * One order for every section, so a bulk pause, resume or cancel only
         * moves rows between sections instead of reshuffling them.
         */
        val TRANSFER_ORDER: Comparator<TransferTask> =
            compareByDescending<TransferTask> { it.progress }.thenBy { it.createdAt }

        /** Finished transfers read as a history, so the latest one sits on top. */
        val FINISHED_ORDER: Comparator<TransferTask> =
            compareByDescending<TransferTask> { it.completedAt ?: it.updatedAt }
    }
}
