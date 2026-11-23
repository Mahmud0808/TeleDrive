package com.drdisagree.teledrive.core.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DamagedUploads {

    private val _pending = MutableStateFlow<List<String>>(emptyList())
    val pending: StateFlow<List<String>> = _pending.asStateFlow()

    val isShown: Boolean
        get() = _pending.subscriptionCount.value > 0

    fun add(fileName: String) = _pending.update { it + fileName }

    fun dismiss() = _pending.update { emptyList() }
}
