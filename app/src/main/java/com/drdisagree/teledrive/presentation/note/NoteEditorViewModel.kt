package com.drdisagree.teledrive.presentation.note

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.presentation.common.toUserMessage
import com.drdisagree.teledrive.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteEditorUiState(
    val title: String = "",
    val body: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val isNew: Boolean = true
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileRepository: FileRepository,
    private val transferRepository: TransferRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.NoteEditor>()

    private val _uiState = MutableStateFlow(NoteEditorUiState(isNew = route.fileId == null))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()

    init {
        val fileId = route.fileId
        if (fileId == null) {
            _uiState.update { it.copy(body = route.sharedText.orEmpty(), loading = false) }
        } else {
            viewModelScope.launch { load(fileId) }
        }
    }

    private suspend fun load(fileId: String) {
        when (val result = fileRepository.readNote(fileId)) {
            is AppResult.Success -> _uiState.update {
                it.copy(title = route.title.orEmpty(), body = result.value, loading = false)
            }

            is AppResult.Failure -> {
                _messages.tryEmit(result.error.toUserMessage(context))
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun setTitle(value: String) = _uiState.update { it.copy(title = value) }

    fun setBody(value: String) = _uiState.update { it.copy(body = value) }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.body.isBlank()) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = fileRepository.saveNote(
                fileId = route.fileId,
                folderId = route.folderId,
                title = state.title.trim(),
                body = state.body.trim()
            )
            when (result) {
                is AppResult.Success -> {
                    transferRepository.enqueueUpload(result.value)
                    _messages.tryEmit(context.getString(R.string.note_saved))
                    _saved.tryEmit(Unit)
                }

                is AppResult.Failure -> {
                    _messages.tryEmit(result.error.toUserMessage(context))
                    _uiState.update { it.copy(saving = false) }
                }
            }
        }
    }
}
