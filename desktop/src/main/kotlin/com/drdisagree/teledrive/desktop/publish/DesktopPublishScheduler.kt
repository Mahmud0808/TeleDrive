package com.drdisagree.teledrive.desktop.publish

import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.publish.PublishOutboxDrainer
import com.drdisagree.teledrive.core.publish.PublishScheduler
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DesktopPublishScheduler(
    private val drainer: PublishOutboxDrainer,
    dispatchers: DispatcherProvider
) : PublishScheduler {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val draining = AtomicBoolean(false)

    override fun kick() {
        if (!draining.compareAndSet(false, true)) return
        scope.launch {
            try {
                var attempts = 0
                while (!drainer.drain(isStopped = { false }) && attempts++ < MAX_ATTEMPTS) {
                    delay(RETRY_DELAY_SECONDS.seconds)
                }
            } finally {
                draining.set(false)
            }
        }
    }

    private companion object {
        const val RETRY_DELAY_SECONDS = 30
        const val MAX_ATTEMPTS = 10
    }
}
