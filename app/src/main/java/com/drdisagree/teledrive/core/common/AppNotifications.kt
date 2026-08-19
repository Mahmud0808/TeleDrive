package com.drdisagree.teledrive.core.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.drdisagree.teledrive.presentation.MainActivity
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.drdisagree.teledrive.R

@Singleton
class AppNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    @SuppressLint("MissingPermission")
    fun notifyFailure(title: String, message: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FAILURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(screenIntent(DESTINATION_TRANSFERS, REQUEST_FAILURE))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FAILURE, notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyBackupResult(title: String, message: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(screenIntent(DESTINATION_TRANSFERS, REQUEST_BACKUP))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BACKUP, notification)
        }
    }

    /** Opens the app on [destination], reusing the running task when there is one. */
    fun screenIntent(destination: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DESTINATION
            putExtra(EXTRA_DESTINATION, destination)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun createChannels() {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRANSFERS,
                context.getString(R.string.notification_channel_transfers),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.notification_channel_transfers_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BACKUP,
                context.getString(R.string.notification_channel_backup),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.notification_channel_backup_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FAILURES,
                context.getString(R.string.notification_channel_failures),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notification_channel_failures_desc) }
        )
    }

    companion object {
        const val ACTION_OPEN_DESTINATION = "com.drdisagree.teledrive.OPEN"
        const val EXTRA_DESTINATION = "destination"
        const val DESTINATION_TRANSFERS = "transfers"
        const val DESTINATION_FILES = "files"
        private const val REQUEST_FAILURE = 1
        private const val REQUEST_BACKUP = 2
        private const val REQUEST_QUEUE = 3
        const val CHANNEL_TRANSFERS = "transfers"
        const val CHANNEL_BACKUP = "backup"
        const val CHANNEL_FAILURES = "failures"
        const val NOTIFICATION_ID_TRANSFER_QUEUE = 1001
        const val NOTIFICATION_ID_BACKUP = 1002
        const val NOTIFICATION_ID_FAILURE = 1003
    }
}
