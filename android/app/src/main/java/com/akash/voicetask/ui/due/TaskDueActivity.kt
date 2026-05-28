package com.akash.voicetask.ui.due

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akash.voicetask.notification.NotificationHelper
import com.akash.voicetask.notification.RingtoneService
import com.akash.voicetask.ui.theme.VoiceTaskTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TaskDueActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_TASK_TITLE = "taskTitle"
        const val EXTRA_OPEN_RESCHEDULE = "openReschedule"
    }

    private val viewModel: TaskDueViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: run { finish(); return }
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Reminder"
        val openReschedule = intent.getBooleanExtra(EXTRA_OPEN_RESCHEDULE, false)

        // Stop ringtone as soon as activity is shown
        stopRingtone()

        setContent {
            VoiceTaskTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState by viewModel.uiState.collectAsState()

                    // Auto-open mic if launched from "Reschedule" notification action
                    LaunchedEffect(openReschedule) {
                        if (openReschedule && uiState is TaskDueViewModel.UiState.Idle) {
                            viewModel.startListening()
                        }
                    }

                    // Dismiss when done
                    LaunchedEffect(uiState) {
                        when (uiState) {
                            is TaskDueViewModel.UiState.Completed,
                            is TaskDueViewModel.UiState.Rescheduled -> {
                                NotificationHelper.dismissNotification(this@TaskDueActivity, taskId)
                                finish()
                            }
                            else -> {}
                        }
                    }

                    TaskDueScreen(
                        taskTitle = taskTitle,
                        uiState = uiState,
                        onComplete = { viewModel.markComplete(taskId) },
                        onStartReschedule = { viewModel.startListening() },
                        onStopReschedule = { viewModel.stopListeningAndReschedule(taskId) },
                        onCancelReschedule = { viewModel.cancelListening() },
                        onRetry = { viewModel.retryFromError() }
                    )
                }
            }
        }
    }

    private fun stopRingtone() {
        startService(Intent(this, RingtoneService::class.java).apply {
            action = RingtoneService.ACTION_STOP
        })
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }
}

@Composable
fun TaskDueScreen(
    taskTitle: String,
    uiState: TaskDueViewModel.UiState,
    onComplete: () -> Unit,
    onStartReschedule: () -> Unit,
    onStopReschedule: () -> Unit,
    onCancelReschedule: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Task Due",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = taskTitle,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            when (uiState) {
                is TaskDueViewModel.UiState.Idle -> {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mark Complete")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onStartReschedule,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reschedule with Voice")
                    }
                }

                is TaskDueViewModel.UiState.Listening -> {
                    Text(
                        text = "Listening...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Speak the new date and time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onStopReschedule,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onCancelReschedule,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }

                is TaskDueViewModel.UiState.Processing -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Processing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is TaskDueViewModel.UiState.Error -> {
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try Again")
                    }
                }

                is TaskDueViewModel.UiState.Completed,
                is TaskDueViewModel.UiState.Rescheduled -> {
                    // Activity will finish — show nothing
                    CircularProgressIndicator()
                }
            }
        }
    }
}
