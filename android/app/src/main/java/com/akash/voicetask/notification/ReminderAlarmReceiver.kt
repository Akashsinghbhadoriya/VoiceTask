package com.akash.voicetask.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        const val PREFS_NAME = "voice_task_prefs"
        const val KEY_VOICE_STARTED_PREFIX = "voice_started_"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
        val taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Reminder"

        Log.d(TAG, "Alarm fired for task $taskId")

        // Set deduplication flag so the QStash REMINDER_FIRED FCM (if it arrives)
        // knows the local alarm already handled voice for this task
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_VOICE_STARTED_PREFIX + taskId, System.currentTimeMillis())
            .apply()

        // Show notification immediately (no I/O — safe within onReceive time limit)
        NotificationHelper.showReminderNotification(context, taskId, taskTitle)

        // Start repeating voice announcement service
        val ttsIntent = Intent(context, TtsAnnouncementService::class.java).apply {
            action = TtsAnnouncementService.ACTION_START
            putExtra(TtsAnnouncementService.EXTRA_TASK_ID, taskId)
            putExtra(TtsAnnouncementService.EXTRA_TASK_TITLE, taskTitle)
        }
        context.startForegroundService(ttsIntent)

        // Dispatch WorkManager job to mark task COMPLETED via API
        val inputData = Data.Builder()
            .putString(MarkTaskCompleteWorker.KEY_TASK_ID, taskId)
            .build()

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<MarkTaskCompleteWorker>()
                .setInputData(inputData)
                .build()
        )
    }
}
