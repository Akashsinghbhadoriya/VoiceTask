package com.akash.voicetask.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.akash.voicetask.data.remote.ApiService
import com.akash.voicetask.data.remote.DeviceRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                apiService.registerDevice(DeviceRequest(fcmToken = token, platform = "android"))
                android.util.Log.d("FcmService", "Device registered with token: $token")
            } catch (e: Exception) {
                android.util.Log.e("FcmService", "Failed to register device", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        when (message.data["type"]) {
            "SCHEDULE_ALARM" -> {
                val taskId = message.data["taskId"] ?: return
                val fireAtMs = message.data["fireAt"]?.toLongOrNull() ?: return
                val title = message.data["title"] ?: "Task Reminder"
                alarmScheduler.scheduleAlarm(taskId, title, fireAtMs)
                android.util.Log.d("FcmService", "Scheduled alarm for task $taskId at $fireAtMs")
            }
            "CANCEL_ALARM" -> {
                val taskId = message.data["taskId"] ?: return
                alarmScheduler.cancelAlarm(taskId)
                android.util.Log.d("FcmService", "Cancelled alarm for task $taskId")
            }
            "REMINDER_FIRED" -> {
                // QStash fallback path — fires for offline devices that missed their local alarm.
                // For online devices the local AlarmManager already handled voice; the deduplication
                // flag prevents a second announcement from firing.
                val taskId = message.data["taskId"] ?: return
                val title = message.data["title"] ?: "Task Reminder"

                val prefs = getSharedPreferences(ReminderAlarmReceiver.PREFS_NAME, MODE_PRIVATE)
                val voiceStartedAt = prefs.getLong(
                    ReminderAlarmReceiver.KEY_VOICE_STARTED_PREFIX + taskId, 0L
                )
                val alreadyHandledByLocalAlarm =
                    voiceStartedAt > 0L && (System.currentTimeMillis() - voiceStartedAt) < 30_000L

                NotificationHelper.showReminderNotification(this, taskId, title)

                if (!alreadyHandledByLocalAlarm) {
                    android.util.Log.d("FcmService", "REMINDER_FIRED: starting TTS for offline task $taskId")
                    val ttsIntent = Intent(this, TtsAnnouncementService::class.java).apply {
                        action = TtsAnnouncementService.ACTION_START
                        putExtra(TtsAnnouncementService.EXTRA_TASK_ID, taskId)
                        putExtra(TtsAnnouncementService.EXTRA_TASK_TITLE, title)
                    }
                    startForegroundService(ttsIntent)
                } else {
                    android.util.Log.d("FcmService", "REMINDER_FIRED: local alarm already handled voice for task $taskId — skipping TTS")
                }
            }
            else -> {
                // Unknown/legacy message type — show a plain notification, no voice
                val title = message.notification?.title ?: "VoiceTask"
                val body = message.notification?.body ?: "Task reminder"
                val taskId = message.data["taskId"] ?: ""
                showNotification(title, body, taskId)
            }
        }
    }

    private fun showNotification(title: String, body: String, taskId: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create intent for tapping notification
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("voicetask://tasks/detail?taskId=$taskId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            taskId.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationId = taskId.hashCode()
        notificationManager.notify(notificationId, notification)
    }
}
