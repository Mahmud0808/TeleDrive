package com.drdisagree.teledrive.presentation.proxy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.telegram.ProxyLink
import com.drdisagree.teledrive.core.proxy.ProxyProbeResult
import com.drdisagree.teledrive.core.telegram.TelegramProxy
import com.drdisagree.teledrive.domain.model.ProxyServer
import com.drdisagree.teledrive.domain.repository.ProxyRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.presentation.common.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class ProxyViewModel(
    private val context: Context,
    private val proxyRepository: ProxyRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val reachability = MutableStateFlow<Map<String, ProxyReachability>>(emptyMap())
    private val testJobs = mutableMapOf<String, Job>()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val uiState: StateFlow<ProxyUiState> = combine(
        proxyRepository.observeProxies(),
        settingsRepository.preferences.map { it.proxyEnabled }.distinctUntilChanged(),
        reachability
    ) { proxies, enabled, results ->
        ProxyUiState(
            proxies = proxies,
            enabled = enabled,
            loading = false,
            reachability = results
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProxyUiState())

    fun save(proxy: ProxyServer) {
        testJobs.remove(proxy.id)?.cancel()
        viewModelScope.launch {
            reachability.update { it - proxy.id }
            report(proxyRepository.save(proxy))
        }
    }

    fun delete(id: String) {
        testJobs.remove(id)?.cancel()
        viewModelScope.launch {
            reachability.update { it - id }
            report(proxyRepository.delete(id))
        }
    }

    fun select(id: String) {
        viewModelScope.launch { report(proxyRepository.select(id)) }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val result = proxyRepository.setEnabled(enabled)
            if (result is AppResult.Failure && result.error is AppError.NotFound) {
                _messages.tryEmit(context.getString(R.string.proxy_add_one_first))
                return@launch
            }
            report(result)
        }
    }

    fun test(proxy: ProxyServer) {
        testJobs[proxy.id]?.cancel()
        testJobs[proxy.id] = viewModelScope.launch {
            if (reachability.value.containsKey(proxy.id)) {
                reachability.update { it - proxy.id }
                delay(CLEAR_DELAY_MS.milliseconds)
            }
            reachability.update { it + (proxy.id to ProxyReachability.TESTING) }
            val result = proxyRepository.test(proxy)
            reachability.update { it + (proxy.id to result.toReachability()) }
            if (result is AppResult.Failure) report(result)
        }
    }

    /** Accepts a shared `tg://proxy` or `t.me` link and saves what it describes. */
    fun importLink(link: String) {
        val parsed = ProxyLink.parse(link)
        if (parsed == null) {
            _messages.tryEmit(context.getString(R.string.proxy_link_not_recognised))
            return
        }
        save(parsed.toServer())
        _messages.tryEmit(context.getString(R.string.proxy_link_imported, parsed.host))
    }

    private fun AppResult<ProxyProbeResult>.toReachability(): ProxyReachability = when (this) {
        is AppResult.Success -> when (value) {
            ProxyProbeResult.ANSWERED -> ProxyReachability.ANSWERED
            else -> ProxyReachability.REACHABLE
        }

        is AppResult.Failure -> ProxyReachability.UNREACHABLE
    }

    private fun report(result: AppResult<Unit>) {
        if (result is AppResult.Failure) {
            _messages.tryEmit(result.error.toUserMessage(context))
        }
    }

    private fun TelegramProxy.toServer() = ProxyServer(
        id = UUID.randomUUID().toString(),
        label = host,
        type = type,
        host = host,
        port = port,
        username = username,
        password = password,
        secret = secret
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val CLEAR_DELAY_MS = 220L
    }
}
