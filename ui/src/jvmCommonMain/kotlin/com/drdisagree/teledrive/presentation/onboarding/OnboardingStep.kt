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

    fun displayNumber(counted: List<OnboardingStep>): Int =
        counted.indexOfLast { it.ordinal <= ordinal }.coerceAtLeast(0) + 1

    companion object {
        fun counted(
            includePermissions: Boolean,
            includeBackup: Boolean
        ): List<OnboardingStep> = buildList {
            add(WELCOME)
            add(API_CREDENTIALS)
            add(PHONE)
            add(CODE)
            if (includePermissions) add(PERMISSIONS)
            add(CHANNEL_SELECT)
            if (includeBackup) add(BACKUP_SETUP)
        }
    }
}
