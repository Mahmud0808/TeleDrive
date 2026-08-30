package com.drdisagree.teledrive.desktop.files

import com.drdisagree.teledrive.core.files.PendingShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Desktop has no share sheet, so this only ever holds what the app puts in. */
class DesktopPendingShare : PendingShare {

    private val _uris = MutableStateFlow<List<String>>(emptyList())
    override val uris: StateFlow<List<String>> = _uris.asStateFlow()

    private val _text = MutableStateFlow<String?>(null)
    override val text: StateFlow<String?> = _text.asStateFlow()

    override fun offer(incoming: List<String>) {
        if (incoming.isEmpty()) return
        _uris.update { incoming }
    }

    override fun clear() = _uris.update { emptyList() }

    override fun offerText(incoming: String?) {
        if (incoming.isNullOrBlank()) return
        _text.update { incoming }
    }

    override fun clearText() = _text.update { null }
}
