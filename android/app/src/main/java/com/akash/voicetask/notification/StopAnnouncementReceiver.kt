package com.akash.voicetask.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopAnnouncementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Stop ringtone service
        context.startService(
            Intent(context, RingtoneService::class.java).apply {
                action = RingtoneService.ACTION_STOP
            }
        )
    }
}
