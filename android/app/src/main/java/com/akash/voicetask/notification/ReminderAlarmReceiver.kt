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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID) ?: return
        val taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Reminder"

        Log.d(TAG, "Alarm fired for task $taskId")

        // Show notification immediately (no I/O — safe within onReceive time limit)
        NotificationHelper.showReminderNotification(context, taskId, taskTitle)

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
