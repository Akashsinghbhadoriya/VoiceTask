package com.akash.voicetask.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopAnnouncementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TtsAnnouncementService.EXTRA_TASK_ID)
        val stopIntent = Intent(context, TtsAnnouncementService::class.java).apply {
            action = TtsAnnouncementService.ACTION_STOP
            putExtra(TtsAnnouncementService.EXTRA_TASK_ID, taskId)
        }
        context.startService(stopIntent)
    }
}
