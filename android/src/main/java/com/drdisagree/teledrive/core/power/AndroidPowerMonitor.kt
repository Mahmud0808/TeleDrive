package com.drdisagree.teledrive.core.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidPowerMonitor(private val context: Context) : PowerMonitor {

    override val charging: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(intent.isPluggedIn())
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        trySend(sticky.isPluggedIn())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    override fun isCharging(): Boolean = ContextCompat.registerReceiver(
        context,
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED
    ).isPluggedIn()

    private fun Intent?.isPluggedIn(): Boolean =
        (this?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
}
