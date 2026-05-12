package com.akash.voicetask.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.ArrayDeque
import java.util.Locale

class TtsAnnouncementService : Service() {

    companion object {
        private const val TAG = "TtsAnnouncementService"
        const val ACTION_START = "com.akash.voicetask.TTS_START"
        const val ACTION_STOP = "com.akash.voicetask.TTS_STOP"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_TASK_TITLE = "taskTitle"
        private const val FOREGROUND_NOTIFICATION_ID = 9999
        const val CHANNEL_ID_TTS = "tts_announcement"
        private const val REPEAT_INTERVAL_MS = 2000L
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val taskQueue = ArrayDeque<Pair<String, String>>()
    private var currentTaskId: String? = null
    private var currentTaskTitle: String? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var originalAlarmVolume = -1

    private val announceRunnable = object : Runnable {
        override fun run() {
            val title = currentTaskTitle ?: return
            if (!isOnCall()) {
                speakTitle(title)
            }
            handler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                ttsReady = true
                // If a task arrived before TTS was ready, start announcing now
                if (currentTaskTitle != null) {
                    requestAudioFocusAndMaxVolume()
                    startAnnouncing()
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: return START_NOT_STICKY
                handleStart(taskId, taskTitle)
            }
            ACTION_STOP -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                handleStop(taskId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(taskId: String, taskTitle: String) {
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(taskTitle))

        if (currentTaskId == null) {
            currentTaskId = taskId
            currentTaskTitle = taskTitle
            if (ttsReady) {
                requestAudioFocusAndMaxVolume()
                startAnnouncing()
            }
            // else TTS init callback will trigger once ready
        } else if (currentTaskId != taskId) {
            if (taskQueue.none { it.first == taskId }) {
                taskQueue.addLast(Pair(taskId, taskTitle))
                Log.d(TAG, "Queued task $taskId — queue size: ${taskQueue.size}")
            }
        }
    }

    private fun handleStop(taskId: String?) {
        if (taskId == null || taskId == currentTaskId) {
            stopCurrentAndAdvance()
        } else {
            taskQueue.removeIf { it.first == taskId }
            Log.d(TAG, "Removed task $taskId from queue")
        }
    }

    private fun stopCurrentAndAdvance() {
        handler.removeCallbacks(announceRunnable)
        tts?.stop()
        currentTaskId = null
        currentTaskTitle = null

        if (taskQueue.isNotEmpty()) {
            val next = taskQueue.removeFirst()
            currentTaskId = next.first
            currentTaskTitle = next.second
            updateForegroundNotification(next.second)
            if (ttsReady) startAnnouncing()
            Log.d(TAG, "Advancing to queued task: ${next.first}")
        } else {
            releaseAudioFocusAndRestoreVolume()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startAnnouncing() {
        handler.removeCallbacks(announceRunnable)
        handler.post(announceRunnable)
    }

    private fun speakTitle(title: String) {
        tts?.speak(title, TextToSpeech.QUEUE_FLUSH, null, "tts_task_$currentTaskId")
    }

    private fun isOnCall(): Boolean {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.callState != TelephonyManager.CALL_STATE_IDLE
        } catch (e: Exception) {
            false
        }
    }

    private fun isDndActive(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }
        return false
    }

    private fun requestAudioFocusAndMaxVolume() {
        originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val targetVolume = if (isDndActive()) {
            // DND is on — use whichever of alarm or ringtone is currently set louder
            val alarmCurrent = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val ringCurrent = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            val ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val alarmFraction = if (alarmMax > 0) alarmCurrent.toFloat() / alarmMax else 0f
            val ringFraction = if (ringMax > 0) ringCurrent.toFloat() / ringMax else 0f
            if (ringFraction > alarmFraction) {
                (ringFraction * alarmMax).toInt().coerceAtMost(alarmMax)
            } else {
                alarmCurrent
            }
        } else {
            // No DND — play at 100% max
            alarmMax
        }

        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun releaseAudioFocusAndRestoreVolume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        if (originalAlarmVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            originalAlarmVolume = -1
        }
    }

    private fun buildForegroundNotification(taskTitle: String): Notification {
        createTtsChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID_TTS)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(taskTitle)
            .setContentText("Tap Stop to silence the announcement")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(android.R.drawable.ic_delete, "Stop", buildStopPendingIntent(currentTaskId))
            .build()
    }

    private fun updateForegroundNotification(taskTitle: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(taskTitle))
    }

    private fun buildStopPendingIntent(taskId: String?): PendingIntent {
        val stopIntent = Intent(this, StopAnnouncementReceiver::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            this,
            taskId?.hashCode() ?: 0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createTtsChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_TTS,
                "Task Announcements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Repeating voice announcements for task reminders"
                setBypassDnd(true)
                enableVibration(false)
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(announceRunnable)
        tts?.stop()
        tts?.shutdown()
        releaseAudioFocusAndRestoreVolume()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
