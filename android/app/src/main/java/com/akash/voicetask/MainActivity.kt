package com.akash.voicetask

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.akash.voicetask.navigation.AppNavigation
import com.akash.voicetask.ui.theme.VoiceTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // After notification dialog is dismissed (granted or denied), check exact alarm
            requestExactAlarmPermissionIfNeeded()
        }

    private val requestExactAlarmSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()
        setContent {
            VoiceTaskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check exact alarm permission in case user returned from settings
        requestExactAlarmPermissionIfNeeded()
    }

    private fun requestRequiredPermissions() {
        // POST_NOTIFICATIONS — runtime permission on Android 13+
        // Exact alarm permission is requested in the callback after this dialog is dismissed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return // exact alarm checked in callback above
        }

        // Notification not needed (already granted or API < 33) — check alarm directly
        requestExactAlarmPermissionIfNeeded()
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        // SCHEDULE_EXACT_ALARM — special app access on Android 12+, must send user to settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                requestExactAlarmSettings.launch(intent)
            }
        }
    }
}
