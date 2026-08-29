package com.drdisagree.teledrive.core.files

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Files handed to the app from another app's share sheet, waiting for the user
 * to choose where they land. Held outside the back stack so the hand-off
 * survives the navigation that follows the intent.
 */
class AndroidPendingShare : PendingShare {

    private val _uris = MutableStateFlow<List<String>>(emptyList())
    override val uris: StateFlow<List<String>> = _uris.asStateFlow()

    override fun offer(incoming: List<String>) {
        if (incoming.isEmpty()) return
        _uris.update { incoming }
    }

    override fun clear() = _uris.update { emptyList() }

    private val _text = MutableStateFlow<String?>(null)
    override val text: StateFlow<String?> = _text.asStateFlow()

    override fun offerText(incoming: String?) {
        if (incoming.isNullOrBlank()) return
        _text.update { incoming }
    }

    override fun clearText() = _text.update { null }
}
