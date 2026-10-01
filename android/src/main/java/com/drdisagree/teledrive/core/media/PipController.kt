package com.drdisagree.teledrive.core.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A FLAG_SECURE window shows as black in picture-in-picture, so app lock and screenshot blocking
 * withdraw the feature.
 */
class PipController(context: Context) {

    private val supported =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val _inPipMode = MutableStateFlow(false)
    val inPipMode: StateFlow<Boolean> = _inPipMode.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var aspectRatio: Rational? = null
    private var owner: Any? = null
    private var secureWindow = false

    /** Several composed pages publish here, so only the owner of the current ratio may clear it. */
    fun offerVideo(owner: Any, ratio: Rational?) {
        if (ratio == null) {
            if (this.owner != owner) return
            this.owner = null
        } else {
            this.owner = owner
        }
        aspectRatio = ratio
        refresh()
    }

    fun setSecureWindow(secure: Boolean) {
        secureWindow = secure
        refresh()
    }

    fun onModeChanged(inPip: Boolean) {
        _inPipMode.value = inPip
    }

    fun enter(activity: Activity): Boolean {
        if (!_available.value) return false
        val ratio = aspectRatio ?: return false
        return runCatching {
            activity.enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(ratio.clampedForPip()).build()
            )
        }.getOrDefault(false)
    }

    private fun refresh() {
        _available.value = supported && !secureWindow && aspectRatio != null
    }
}

/** The system rejects anything outside 1:2.39 to 2.39:1 with an exception. */
private fun Rational.clampedForPip(): Rational {
    val value = numerator.toFloat() / denominator
    return when {
        value < MIN_PIP_RATIO -> Rational(100, 239)
        value > MAX_PIP_RATIO -> Rational(239, 100)
        else -> this
    }
}

private const val MIN_PIP_RATIO = 1f / 2.39f
private const val MAX_PIP_RATIO = 2.39f
