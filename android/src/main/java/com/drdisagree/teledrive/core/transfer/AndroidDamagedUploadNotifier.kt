package com.drdisagree.teledrive.core.transfer

import android.content.Context
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppNotifications

class AndroidDamagedUploadNotifier(
    private val context: Context,
    private val damagedUploads: DamagedUploads,
    private val appNotifications: AppNotifications
) : DamagedUploadNotifier {

    override fun report(fileName: String) {
        if (damagedUploads.isShown) {
            damagedUploads.add(fileName)
        } else {
            appNotifications.notifyFailure(
                title = context.getString(R.string.notification_upload_damaged),
                message = fileName
            )
        }
    }
}
