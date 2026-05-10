package com.akash.voicetask.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.akash.voicetask.data.local.TaskDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    lateinit var taskDao: TaskDao

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.d(TAG, "Boot/update received — rescheduling alarms")

        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = taskDao.getPendingTasksWithDueAt()
                val now = System.currentTimeMillis()
                var scheduled = 0

                for (task in tasks) {
                    val dueAtMs = try {
                        Instant.parse(task.dueAt).toEpochMilli()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse dueAt for task ${task.id}")
                        continue
                    }
                    val fireAtMs = dueAtMs - (task.reminderOffsetMinutes * 60_000L)
                    if (fireAtMs > now) {
                        alarmScheduler.scheduleAlarm(task.id, task.title, fireAtMs)
                        scheduled++
                    }
                }

                Log.d(TAG, "Rescheduled $scheduled alarms after boot")
            } finally {
                pending.finish()
            }
        }
    }
}
