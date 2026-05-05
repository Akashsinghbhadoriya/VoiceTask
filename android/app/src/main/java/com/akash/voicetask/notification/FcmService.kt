package com.akash.voicetask.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Register device with backend
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get API service and register token
                // This would be done via dependency injection in a real implementation
                // For now, we just log it
                android.util.Log.d("FcmService", "New FCM token: $token")
            } catch (e: Exception) {
                android.util.Log.e("FcmService", "Failed to register device", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: "VoiceTask"
        val body = message.notification?.body ?: "Task reminder"
        val taskId = message.data["taskId"] ?: ""

        showNotification(title, body, taskId)
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
