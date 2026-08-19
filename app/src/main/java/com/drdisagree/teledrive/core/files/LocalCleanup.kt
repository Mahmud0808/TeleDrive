package com.drdisagree.teledrive.core.files

import android.content.IntentSender

/**
 * Outcome of removing local copies. [consentRequest] is non-null when Android
 * requires the user to confirm deleting media it did not let this app own.
 */
data class LocalCleanup(
    val deletedCount: Int,
    val consentRequest: IntentSender? = null
)
