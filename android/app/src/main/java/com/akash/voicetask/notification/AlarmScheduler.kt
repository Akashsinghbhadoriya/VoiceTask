package com.akash.voicetask.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_TASK_TITLE = "taskTitle"
    }

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule an exact alarm for [taskId] at [fireAtMillis] (UTC epoch ms).
     * Uses AlarmManager.setAlarmClock() which wakes device from Doze and shows
     * a clock icon in the status bar. On Android 12+ requires SCHEDULE_EXACT_ALARM;
     * if not granted, degrades gracefully (QStash fallback still delivers notification).
     */
    fun scheduleAlarm(taskId: String, taskTitle: String, fireAtMillis: Long) {
        if (fireAtMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "Skipping past alarm for task $taskId (fireAt=$fireAtMillis)")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted — cannot schedule alarm for $taskId")
                return
            }
        }

        val pendingIntent = buildPendingIntent(taskId, taskTitle)

        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            fireAtMillis,
            buildLaunchAppPendingIntent()
        )

        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        Log.d(TAG, "Alarm scheduled for task $taskId at $fireAtMillis")
    }

    /**
     * Cancel a previously scheduled alarm for [taskId].
     */
    fun cancelAlarm(taskId: String) {
        val pendingIntent = buildPendingIntent(taskId, "")
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Alarm cancelled for task $taskId")
    }

    private fun buildPendingIntent(taskId: String, taskTitle: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            // Include taskId in action so alarms for different tasks don't collide
            action = "com.akash.voicetask.ALARM.$taskId"
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildLaunchAppPendingIntent(): PendingIntent {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName) ?: Intent()
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
