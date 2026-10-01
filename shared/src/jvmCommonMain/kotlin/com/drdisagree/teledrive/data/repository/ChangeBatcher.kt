package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.telegram.MessageChange
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Folder renames rewrite every caption inside, so a short wait turns a burst into one pass. */
internal class ChangeBatcher(private val windowMs: Long) {

    suspend fun run(
        changes: Flow<MessageChange>,
        apply: suspend (updated: Set<Long>, deleted: Set<Long>) -> Unit
    ) = coroutineScope {
        val inbox = Channel<MessageChange>(Channel.UNLIMITED)
        launch { changes.collect { inbox.send(it) } }
        while (true) {
            val batch = mutableListOf(inbox.receive())
            delay(windowMs.milliseconds)
            while (true) batch += inbox.tryReceive().getOrNull() ?: break
            val deleted = batch.filterIsInstance<MessageChange.Deleted>()
                .flatMap { it.messageIds }
                .toSet()
            val updated = batch.filterIsInstance<MessageChange.Updated>()
                .map { it.messageId }
                .filterNot { it in deleted }
                .toSet()
            apply(updated, deleted)
        }
    }
}
