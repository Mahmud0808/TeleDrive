package com.drdisagree.teledrive.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.domain.model.Exclusion
import com.drdisagree.teledrive.domain.model.ExclusionType
import com.drdisagree.teledrive.domain.repository.ExclusionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ExclusionsViewModel @Inject constructor(
    private val exclusionRepository: ExclusionRepository
) : ViewModel() {

    val exclusions: StateFlow<List<Exclusion>> = exclusionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(type: ExclusionType, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch { exclusionRepository.add(type, value) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { exclusionRepository.setEnabled(id, enabled) }
    }

    fun remove(id: String) {
        viewModelScope.launch { exclusionRepository.remove(id) }
    }
}
