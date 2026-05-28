package com.akash.voicetask.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class TaskActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskActionReceiver"
        const val ACTION_COMPLETE = "com.akash.voicetask.ACTION_COMPLETE"
        const val EXTRA_TASK_ID = "taskId"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return

        when (intent.action) {
            ACTION_COMPLETE -> {
                Log.d(TAG, "Complete action tapped for task $taskId")

                // Stop ringtone if playing
                context.startService(
                    Intent(context, RingtoneService::class.java).apply {
                        action = RingtoneService.ACTION_STOP
                    }
                )

                // Enqueue WorkManager to PATCH status = COMPLETED
                val inputData = Data.Builder()
                    .putString(MarkTaskCompleteWorker.KEY_TASK_ID, taskId)
                    .build()
                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequestBuilder<MarkTaskCompleteWorker>()
                        .setInputData(inputData)
                        .build()
                )

                // Dismiss the notification
                NotificationHelper.dismissNotification(context, taskId)
            }
        }
    }
}
