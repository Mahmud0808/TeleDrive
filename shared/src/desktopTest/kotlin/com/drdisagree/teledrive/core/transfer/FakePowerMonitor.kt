package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.power.PowerMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakePowerMonitor(initial: Boolean) : PowerMonitor {
    private val state = MutableStateFlow(initial)
    override val charging: Flow<Boolean> = state
    override fun isCharging(): Boolean = state.value
}
