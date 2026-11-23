package com.recall.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        val prefs = context.getSharedPreferences("recall_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("accepted_terms", false)) return
        if (!prefs.getBoolean("enabled", true)) return

        val serviceIntent = Intent(context, AudioCaptureService::class.java)
            .setAction(AudioCaptureService.ACTION_START)
        try {
            context.startForegroundService(serviceIntent)
        } catch (_: Exception) {
        }
    }
}
