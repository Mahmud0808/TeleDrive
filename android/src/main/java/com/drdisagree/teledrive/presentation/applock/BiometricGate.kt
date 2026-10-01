package com.drdisagree.teledrive.presentation.applock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Falls through without a secure lock, or there would be no way to recover. */
fun requireDeviceOwner(
    activity: FragmentActivity?,
    title: String,
    subtitle: String,
    onDenied: (String?) -> Unit = {},
    onConfirmed: () -> Unit
) {
    val host = activity ?: run {
        onConfirmed()
        return
    }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(host).canAuthenticate(authenticators) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) {
        onConfirmed()
        return
    }

    val prompt = BiometricPrompt(
        host,
        ContextCompat.getMainExecutor(host),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onConfirmed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val silent = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                onDenied(if (silent) null else errString.toString())
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
    )
}
