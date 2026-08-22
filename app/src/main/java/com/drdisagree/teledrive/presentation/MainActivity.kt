package com.drdisagree.teledrive.presentation

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.core.files.PendingShare
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Extends FragmentActivity because androidx BiometricPrompt requires it for
 * the app lock flow.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository


    /**
     * The launch theme can only name a static color, so the splash lands on
     * the closest system tone. Repainting the window with the very color the
     * Compose theme uses removes the seam between splash and first frame.
     */
    private fun applyWindowBackground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val dark = nightMode == Configuration.UI_MODE_NIGHT_YES
        val scheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
        window.setBackgroundDrawable(scheme.background.toArgb().toDrawable())
    }

    private fun observeScreenshotProtection() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.preferences
                    .map { it.appLockEnabled || it.blockScreenCapture }
                    .distinctUntilChanged()
                    .collect { locked ->
                        if (locked) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
            }
        }
    }

    @Inject
    lateinit var pendingShare: PendingShare

    private val destination = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** A share lands on the files screen, where the destination is chosen. */
    private fun handleIntent(intent: Intent?) {
        val shared = sharedUris(intent)
        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
        pendingShare.offer(shared)
        pendingShare.offerText(sharedText)
        destination.value = when {
            shared.isNotEmpty() -> AppNotifications.DESTINATION_FILES
            !sharedText.isNullOrBlank() -> AppNotifications.DESTINATION_NOTE
            else -> destinationOf(intent)
        }
    }

    /** Files another app handed over through the system share sheet. */
    private fun sharedUris(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(
                IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
            )

        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java
            ).orEmpty()

        else -> emptyList()
    }

    private fun destinationOf(intent: Intent?): String? = intent
        ?.takeIf { it.action == AppNotifications.ACTION_OPEN_DESTINATION }
        ?.getStringExtra(AppNotifications.EXTRA_DESTINATION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        applyWindowBackground()
        observeScreenshotProtection()
        handleIntent(intent)
        setContent {
            TeleDriveApp(
                pendingShare = pendingShare,
                notificationDestination = destination.collectAsStateWithLifecycle().value,
                onDestinationHandled = { destination.value = null }
            )
        }
    }
}
