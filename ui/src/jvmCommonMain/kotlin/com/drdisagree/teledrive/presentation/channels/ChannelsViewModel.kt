package com.drdisagree.teledrive.presentation.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.app_drive_recreated
import com.drdisagree.teledrive.resources.channels_creating_drive
import com.drdisagree.teledrive.resources.channels_deleting
import com.drdisagree.teledrive.resources.channels_looking_for_drives
import com.drdisagree.teledrive.resources.channels_opening_drive
import com.drdisagree.teledrive.resources.channels_removed_missing
import com.drdisagree.teledrive.resources.channels_renaming
import com.drdisagree.teledrive.resources.message_created_drive
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.presentation.common.UiText
import com.drdisagree.teledrive.presentation.common.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelsViewModel(
    private val channelRepository: ChannelRepository
) : ViewModel() {

    val channels: StateFlow<List<DriveChannel>> = channelRepository.observeChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _working = MutableStateFlow<UiText?>(null)
    val working: StateFlow<UiText?> = _working.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() = run(UiText.Resource(Res.string.channels_looking_for_drives)) {
        val pruned = channelRepository.pruneDeleted()
        if (pruned is AppResult.Success && pruned.value > 0) {
            _messages.tryEmit(
                UiText.Resource(Res.string.channels_removed_missing, pruned.value)
            )
        }
        val result = channelRepository.refresh()
        if (result is AppResult.Success && result.value.isEmpty()) {
            channelRepository.create(DEFAULT_DRIVE_LABEL).let { created ->
                if (created is AppResult.Success) {
                    channelRepository.switchTo(created.value.chatId)
                    _messages.tryEmit(UiText.Resource(Res.string.app_drive_recreated))
                }
            }
        }
        result
    }

    fun create(label: String) = run(UiText.Resource(Res.string.channels_creating_drive)) {
        when (val result = channelRepository.create(label)) {
            is AppResult.Success -> {
                _messages.tryEmit(
                    UiText.Resource(Res.string.message_created_drive,
                        result.value.displayName
                    )
                )
                channelRepository.switchTo(result.value.chatId)
            }

            is AppResult.Failure -> result
        }
    }

    fun switchTo(chatId: Long) =
        run(UiText.Resource(Res.string.channels_opening_drive)) { channelRepository.switchTo(chatId) }

    fun rename(chatId: Long, label: String) = run(UiText.Resource(Res.string.channels_renaming)) {
        channelRepository.rename(chatId, label)
    }

    fun deleteRemotely(chatId: Long) = run(UiText.Resource(Res.string.channels_deleting)) {
        channelRepository.deleteRemotely(chatId)
    }

    private fun run(label: UiText, block: suspend () -> AppResult<*>) {
        if (_working.value != null) return
        _working.value = label
        viewModelScope.launch {
            val result = block()
            _working.value = null
            if (result is AppResult.Failure) _messages.tryEmit(result.error.toUiText())
        }
    }

    private companion object {
        const val DEFAULT_DRIVE_LABEL = ""
    }
}
