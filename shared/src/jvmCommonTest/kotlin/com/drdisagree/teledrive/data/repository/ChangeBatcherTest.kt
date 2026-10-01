package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.telegram.MessageChange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeBatcherTest {

    private val batches = mutableListOf<Pair<Set<Long>, Set<Long>>>()

    @Test
    fun `a burst of changes is applied in one pass`() = runBlocking {
        val changes = MutableSharedFlow<MessageChange>(extraBufferCapacity = 1_000)
        val job = launch { ChangeBatcher(BURST_WINDOW).run(changes) { u, d -> batches += u to d } }
        changes.subscriptionCount.first { it > 0 }

        repeat(20) {
            changes.emit(MessageChange.Updated(it.toLong()))
            delay(10)
        }
        delay(BURST_WINDOW * 2)
        job.cancel()

        assertEquals(1, batches.size)
        assertEquals(20, batches.single().first.size)
    }

    @Test
    fun `a message updated and deleted in one batch counts as deleted`() = runBlocking {
        val changes = MutableSharedFlow<MessageChange>(extraBufferCapacity = 10)
        val job = launch { ChangeBatcher(WINDOW).run(changes) { u, d -> batches += u to d } }
        changes.subscriptionCount.first { it > 0 }

        changes.emit(MessageChange.Updated(1))
        changes.emit(MessageChange.Updated(2))
        changes.emit(MessageChange.Deleted(listOf(2)))
        delay(WINDOW * 3)
        job.cancel()

        assertEquals(setOf(1L) to setOf(2L), batches.single())
    }

    @Test
    fun `separate bursts are applied separately`() = runBlocking {
        val changes = MutableSharedFlow<MessageChange>(extraBufferCapacity = 10)
        val job = launch { ChangeBatcher(WINDOW).run(changes) { u, d -> batches += u to d } }
        changes.subscriptionCount.first { it > 0 }

        changes.emit(MessageChange.Updated(1))
        delay(WINDOW * 3)
        changes.emit(MessageChange.Updated(2))
        delay(WINDOW * 3)
        job.cancel()

        assertEquals(listOf(setOf(1L), setOf(2L)), batches.map { it.first })
        assertTrue(batches.all { it.second.isEmpty() })
    }

    private companion object {
        const val WINDOW = 100L
        const val BURST_WINDOW = 1_500L
    }
}
