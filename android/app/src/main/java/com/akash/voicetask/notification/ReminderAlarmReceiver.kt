package com.akash.voicetask.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.akash.voicetask.ui.due.TaskDueActivity

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        const val PREFS_NAME = "voice_task_prefs"
        const val KEY_SOUND_ALERT_ENABLED = "sound_alert_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
        val taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Reminder"

        Log.d(TAG, "Alarm fired for task $taskId")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean(KEY_SOUND_ALERT_ENABLED, true)

        // Always show notification with Complete + Reschedule actions
        NotificationHelper.showReminderNotification(context, taskId, taskTitle)

        if (soundEnabled) {
            // Play ringtone via foreground service
            val ringtoneIntent = Intent(context, RingtoneService::class.java).apply {
                action = RingtoneService.ACTION_PLAY
                putExtra(RingtoneService.EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(ringtoneIntent)
            } else {
                context.startService(ringtoneIntent)
            }

            // Launch full-screen due activity
            val activityIntent = Intent(context, TaskDueActivity::class.java).apply {
                putExtra(TaskDueActivity.EXTRA_TASK_ID, taskId)
                putExtra(TaskDueActivity.EXTRA_TASK_TITLE, taskTitle)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
            context.startActivity(activityIntent)
        }
    }
}
