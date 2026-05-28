package com.akash.voicetask.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.akash.voicetask.ui.due.TaskDueActivity

object NotificationHelper {
    // V2 channel uses STREAM_RING so volume buttons work
    const val CHANNEL_ID = "task_reminders_v2"
    private const val CHANNEL_NAME = "Task Reminders"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for task reminders"
                enableVibration(true)
                setBypassDnd(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show a reminder notification with Complete and Reschedule action buttons.
     * Called by ReminderAlarmReceiver (local alarm) and FcmService REMINDER_FIRED (QStash fallback).
     * Sound is only played by the local alarm path via RingtoneService — this notification is silent
     * when called from FcmService to avoid duplicate audio.
     */
    fun showReminderNotification(context: Context, taskId: String, title: String, silent: Boolean = false) {
        createNotificationChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            Intent(context, TaskDueActivity::class.java).apply {
                putExtra(TaskDueActivity.EXTRA_TASK_ID, taskId)
                putExtra(TaskDueActivity.EXTRA_TASK_TITLE, title)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = PendingIntent.getBroadcast(
            context,
            "complete_$taskId".hashCode(),
            Intent(context, TaskActionReceiver::class.java).apply {
                action = TaskActionReceiver.ACTION_COMPLETE
                putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rescheduleIntent = PendingIntent.getActivity(
            context,
            "reschedule_$taskId".hashCode(),
            Intent(context, TaskDueActivity::class.java).apply {
                putExtra(TaskDueActivity.EXTRA_TASK_ID, taskId)
                putExtra(TaskDueActivity.EXTRA_TASK_TITLE, title)
                putExtra(TaskDueActivity.EXTRA_OPEN_RESCHEDULE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Task is due now — mark complete or reschedule")
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "Complete", completeIntent)
            .addAction(0, "Reschedule", rescheduleIntent)
            .apply { if (silent) setSilent(true) }
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(taskId.hashCode(), notification)
    }

    fun dismissNotification(context: Context, taskId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(taskId.hashCode())
    }
}
