package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.ProxyServer
import kotlinx.coroutines.flow.Flow

interface ProxyRepository {

    fun observeProxies(): Flow<List<ProxyServer>>

    suspend fun save(proxy: ProxyServer): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>

    /** Picks which saved proxy to route through, and applies it now. */
    suspend fun select(id: String): AppResult<Unit>

    /** Turns routing on or off without forgetting the saved list. */
    suspend fun setEnabled(enabled: Boolean): AppResult<Unit>

    /** Reapplies the current choice, for a client that has just started. */
    suspend fun applyActive()

    /**
     * Moves to the next saved proxy and reconnects through it. Returns false
     * when there is nothing else to try.
     */
    suspend fun rotate(): AppResult<Boolean>

    /** Succeeds when Telegram answers through [proxy]. */
    suspend fun test(proxy: ProxyServer): AppResult<Unit>

    /**
     * Whether a proxy can be tried at all. Testing runs through Telegram's own
     * client, which does not exist until the API keys are in, so the action is
     * offered only once there is something to test with.
     */
    fun observeTestable(): Flow<Boolean>
}
