package com.drdisagree.teledrive.domain.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.proxy.ProxyProbeResult
import com.drdisagree.teledrive.domain.model.ProxyServer
import kotlinx.coroutines.flow.Flow

interface ProxyRepository {

    fun observeProxies(): Flow<List<ProxyServer>>

    suspend fun save(proxy: ProxyServer): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>

    suspend fun select(id: String): AppResult<Unit>

    suspend fun setEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun applyActive()

    /** Returns false when there is nothing else to try. */
    suspend fun rotate(): AppResult<Boolean>

    suspend fun test(proxy: ProxyServer): AppResult<ProxyProbeResult>
}
