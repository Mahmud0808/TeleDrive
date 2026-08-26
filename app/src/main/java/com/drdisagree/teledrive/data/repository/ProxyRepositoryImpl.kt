package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramAuthState
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.core.telegram.TelegramProxy
import com.drdisagree.teledrive.data.local.dao.ProxyDao
import com.drdisagree.teledrive.data.local.entity.ProxyEntity
import com.drdisagree.teledrive.domain.model.ProxyServer
import com.drdisagree.teledrive.domain.repository.ProxyRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved routes to Telegram, for networks where it is blocked.
 *
 * The list lives here rather than in TDLib because the session database is
 * deleted on an auth reset, and a user who reaches Telegram only through a
 * proxy would otherwise lose the way back in.
 */
@Singleton
class ProxyRepositoryImpl @Inject constructor(
    private val proxyDao: ProxyDao,
    private val settingsRepository: SettingsRepository,
    private val telegramClient: TelegramClient
) : ProxyRepository {

    override fun observeProxies(): Flow<List<ProxyServer>> = combine(
        proxyDao.observeAll(),
        settingsRepository.preferences.map { it.activeProxyId }
    ) { proxies, activeId ->
        proxies.map { it.toDomain(isActive = it.id == activeId) }
    }

    override suspend fun save(proxy: ProxyServer): AppResult<Unit> {
        proxyDao.upsert(proxy.toEntity())
        val prefs = settingsRepository.preferences.first()
        if (prefs.activeProxyId.isBlank()) {
            settingsRepository.update { it.copy(activeProxyId = proxy.id) }
        }
        return applyAndReport()
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        proxyDao.delete(id)
        val prefs = settingsRepository.preferences.first()
        if (prefs.activeProxyId == id) {
            val fallback = proxyDao.all().firstOrNull()
            settingsRepository.update {
                it.copy(
                    activeProxyId = fallback?.id.orEmpty(),
                    proxyEnabled = it.proxyEnabled && fallback != null
                )
            }
        }
        return applyAndReport()
    }

    override suspend fun select(id: String): AppResult<Unit> {
        settingsRepository.update { it.copy(activeProxyId = id) }
        return applyAndReport()
    }

    override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> {
        if (enabled && proxyDao.all().isEmpty()) {
            return AppResult.Failure(
                AppError.NotFound
            )
        }
        settingsRepository.update { it.copy(proxyEnabled = enabled) }
        return applyAndReport()
    }

    override fun observeTestable(): Flow<Boolean> = telegramClient.authState.map { state ->
        state !is TelegramAuthState.Uninitialized &&
                state !is TelegramAuthState.Initializing &&
                state !is TelegramAuthState.Closed
    }

    override suspend fun rotate(): AppResult<Boolean> {
        val saved = proxyDao.all()
        if (saved.size < 2) return AppResult.Success(false)
        val activeId = settingsRepository.preferences.first().activeProxyId
        val current = saved.indexOfFirst { it.id == activeId }
        val next = saved[(current + 1).mod(saved.size)]
        SafeLog.d(TAG, "Switching to the next saved proxy")
        return when (val result = select(next.id)) {
            is AppResult.Success -> AppResult.Success(true)
            is AppResult.Failure -> result
        }
    }

    override suspend fun applyActive() {
        runCatching { telegramClient.applyProxy(activeProxy()) }
            .onFailure { SafeLog.w(TAG, "Could not apply the proxy", it) }
    }

    override suspend fun test(proxy: ProxyServer): AppResult<Unit> = try {
        telegramClient.testProxy(proxy.toTelegram())
        AppResult.Success(Unit)
    } catch (e: TelegramException) {
        SafeLog.w(TAG, "Proxy test failed", e)
        AppResult.Failure(
            if (e.code == UNAUTHORIZED) {
                AppError.AuthenticationRequired
            } else {
                AppError.TelegramError(e.code, e.message)
            }
        )
    }

    @Volatile
    private var appliedProxy: TelegramProxy? = null

    private suspend fun activeProxy(): TelegramProxy? {
        val prefs = settingsRepository.preferences.first()
        if (!prefs.proxyEnabled) return null
        val entity = prefs.activeProxyId.takeIf { it.isNotBlank() }?.let { proxyDao.byId(it) }
        return entity?.toDomain(isActive = true)?.toTelegram()
    }

    private suspend fun applyAndReport(): AppResult<Unit> = try {
        val target = activeProxy()
        telegramClient.applyProxy(target)
        if (target != appliedProxy) {
            appliedProxy = target
            telegramClient.reconnect()
        }
        AppResult.Success(Unit)
    } catch (e: TelegramException) {
        AppResult.Failure(
            AppError.TelegramError(e.code, e.message)
        )
    }

    private fun ProxyEntity.toDomain(isActive: Boolean) = ProxyServer(
        id = id,
        label = label,
        type = type,
        host = host,
        port = port,
        username = username,
        password = password,
        secret = secret,
        isActive = isActive
    )

    private fun ProxyServer.toEntity() = ProxyEntity(
        id = id,
        label = label,
        type = type,
        host = host,
        port = port,
        username = username?.takeIf { it.isNotBlank() },
        password = password?.takeIf { it.isNotBlank() },
        secret = secret?.takeIf { it.isNotBlank() },
        addedAt = System.currentTimeMillis()
    )

    private fun ProxyServer.toTelegram() = TelegramProxy(
        type = type,
        host = host,
        port = port,
        username = username,
        password = password,
        secret = secret
    )

    private companion object {
        const val TAG = "ProxyRepository"
        const val UNAUTHORIZED = 401
    }
}
