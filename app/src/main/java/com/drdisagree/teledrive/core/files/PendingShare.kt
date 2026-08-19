package com.drdisagree.teledrive.core.files

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Files handed to the app from another app's share sheet, waiting for the user
 * to choose where they land. Held outside the back stack so the hand-off
 * survives the navigation that follows the intent.
 */
@Singleton
class PendingShare @Inject constructor() {

    private val _uris = MutableStateFlow<List<Uri>>(emptyList())
    val uris: StateFlow<List<Uri>> = _uris.asStateFlow()

    fun offer(incoming: List<Uri>) {
        if (incoming.isEmpty()) return
        _uris.update { incoming }
    }

    fun clear() = _uris.update { emptyList() }
}
