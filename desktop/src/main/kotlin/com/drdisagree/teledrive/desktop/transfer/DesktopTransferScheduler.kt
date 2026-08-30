package com.drdisagree.teledrive.desktop.transfer

import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.transfer.TransferDrainResult
import com.drdisagree.teledrive.core.transfer.TransferQueueDrainer
import com.drdisagree.teledrive.core.transfer.TransferScheduler
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DesktopTransferScheduler(
    private val drainer: TransferQueueDrainer,
    dispatchers: DispatcherProvider
) : TransferScheduler {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val draining = AtomicBoolean(false)

    override fun kick(allowMetered: Boolean) {
        if (!draining.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (drainer.drain(
                        isStopped = { false },
                        onTerminalFailure = { }
                    ) == TransferDrainResult.INTERRUPTED
                ) {
                    delay(RETRY_DELAY_SECONDS.seconds)
                }
            } finally {
                draining.set(false)
            }
        }
    }

    override fun rekick(allowMetered: Boolean) {
        kick(allowMetered)
    }

    private companion object {
        const val RETRY_DELAY_SECONDS = 15
    }
}
