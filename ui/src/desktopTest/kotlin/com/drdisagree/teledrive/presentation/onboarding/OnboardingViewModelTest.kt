package com.drdisagree.teledrive.presentation.onboarding

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.telegram.TelegramAuthState
import com.drdisagree.teledrive.domain.model.DriveChannel
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.TelegramAuthRepository
import com.drdisagree.teledrive.presentation.platform.PlatformCapabilities
import com.drdisagree.teledrive.testing.unused
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed drive refresh is shown instead of creating a duplicate drive`() = runTest(dispatcher) {
        val auth = FakeAuth()
        val channels = FakeChannels(
            refreshResult = AppResult.Failure(
                AppError.TelegramError(408, "Telegram did not respond")
            )
        )
        val viewModel = OnboardingViewModel(
            telegramAuthRepository = auth,
            settingsRepository = unused(),
            syncRepository = unused(),
            maintenanceScheduler = unused(),
            channelRepository = channels,
            standardFolderPaths = unused(),
            platformCapabilities = FakeCapabilities
        )

        auth.state.value = TelegramAuthState.Ready
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.CHANNEL_SELECT, state.step)
        assertNotNull(state.error)
        assertEquals(0, channels.createCalls)
    }

    private class FakeAuth : TelegramAuthRepository by unused() {
        val state = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Initializing)
        override val authState: StateFlow<TelegramAuthState> = state
    }

    private class FakeChannels(
        private val refreshResult: AppResult<List<DriveChannel>>
    ) : ChannelRepository by unused() {
        var createCalls = 0

        override suspend fun refresh(): AppResult<List<DriveChannel>> = refreshResult

        override suspend fun create(label: String): AppResult<DriveChannel> {
            createCalls++
            return AppResult.Failure(AppError.LastDriveRemaining)
        }
    }

    private object FakeCapabilities : PlatformCapabilities {
        override val supportsAutoBackup = false
        override val requiresPermissions = false
        override val supportsPullToRefresh = false
        override val supportsAppLock = false
        override val supportsScreenCaptureBlocking = false
        override val supportsDynamicColor = false
    }
}
