package com.drdisagree.teledrive.core.telegram

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkSettler<T : Any>(
    private val scope: CoroutineScope,
    private val settleMs: Long,
    private val onChange: () -> Unit
) {

    private val lock = Any()
    private var seeded = false
    private var current: T? = null
    private var reported: T? = null
    private var pending: Job? = null

    fun available(network: T) = update { current = network }

    fun lost(network: T) = update { if (current == network) current = null }

    private fun update(change: () -> Unit) {
        synchronized(lock) {
            change()
            if (!seeded) {
                seeded = true
                reported = current
                return
            }
            pending?.cancel()
            pending = scope.launch {
                delay(settleMs)
                val changed = synchronized(lock) {
                    (current != reported).also { if (it) reported = current }
                }
                if (changed) onChange()
            }
        }
    }
}
