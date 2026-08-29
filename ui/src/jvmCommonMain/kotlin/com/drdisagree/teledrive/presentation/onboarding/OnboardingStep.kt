package com.drdisagree.teledrive.presentation.onboarding

enum class OnboardingStep {
    WELCOME,
    API_CREDENTIALS,
    PHONE,
    EMAIL_ADDRESS,
    EMAIL_CODE,
    CODE,
    PASSWORD,
    PERMISSIONS,
    CHANNEL_SELECT,
    BACKUP_SETUP,
    DONE;

    val displayNumber: Int
        get() = COUNTED.indexOfLast { it.ordinal <= ordinal }.coerceAtLeast(0) + 1

    companion object {
        private val COUNTED = listOf(
            WELCOME,
            API_CREDENTIALS,
            PHONE,
            CODE,
            PERMISSIONS,
            CHANNEL_SELECT,
            BACKUP_SETUP
        )

        val displayTotal: Int = COUNTED.size
    }
}
