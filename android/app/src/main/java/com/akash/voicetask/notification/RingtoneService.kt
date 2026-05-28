package com.akash.voicetask.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class RingtoneService : Service() {

    companion object {
        private const val TAG = "RingtoneService"
        const val ACTION_PLAY = "com.akash.voicetask.RINGTONE_PLAY"
        const val ACTION_STOP = "com.akash.voicetask.RINGTONE_STOP"
        const val EXTRA_TASK_ID = "taskId"
        private const val FOREGROUND_ID = 9998
        const val CHANNEL_ID = "ringtone_service"
    }

    private var ringtone: android.media.Ringtone? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                startForeground(FOREGROUND_ID, buildForegroundNotification())
                playRingtone()
            }
            ACTION_STOP -> stopRingtone()
        }
        return START_NOT_STICKY
    }

    private fun playRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
            Log.d(TAG, "Ringtone playing")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ringtone", e)
            stopSelf()
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
        Log.d(TAG, "Ringtone stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildForegroundNotification(): Notification {
        createChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Task Reminder")
            .setContentText("A task is due now")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ringtone Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        ringtone?.stop()
        ringtone = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
