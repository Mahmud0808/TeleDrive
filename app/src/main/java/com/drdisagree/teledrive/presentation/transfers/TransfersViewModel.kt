package com.drdisagree.teledrive.presentation.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferTask
import com.drdisagree.teledrive.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransfersUiState(
    val active: List<TransferTask> = emptyList(),
    val paused: List<TransferTask> = emptyList(),
    val failed: List<TransferTask> = emptyList(),
    val completed: List<TransferTask> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class TransfersViewModel @Inject constructor(
    private val transferRepository: TransferRepository
) : ViewModel() {

    val uiState: StateFlow<TransfersUiState> = transferRepository.observeAll()
        .map { transfers ->
            val ordered = transfers.sortedWith(TRANSFER_ORDER)
            TransfersUiState(
                active = ordered.filter { it.state.isActive },
                paused = ordered.filter { it.state == TransferState.PAUSED },
                failed = ordered.filter { it.state == TransferState.FAILED },
                completed = ordered.filter { it.state == TransferState.COMPLETED }.sortedWith(FINISHED_ORDER),
                loading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransfersUiState())

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
