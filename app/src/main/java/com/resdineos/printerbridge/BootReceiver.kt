package com.resdineos.printerbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts the bridge automatically after the tablet reboots, so the printer is
 * ready without anyone opening the app. Only starts if a printer was chosen
 * before (saved in prefs); otherwise there is nothing to connect to.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(BridgeService.KEY_MAC, null) == null) return
        // No extras: the service reloads the saved printer from prefs itself.
        ContextCompat.startForegroundService(context, Intent(context, BridgeService::class.java))
    }
}
