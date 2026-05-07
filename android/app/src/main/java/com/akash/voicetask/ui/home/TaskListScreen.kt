package com.akash.voicetask.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akash.voicetask.domain.model.Task
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun formatDateTime(isoString: String?): String {
    if (isoString == null) return ""
    return try {
        // Try parsing as ISO format (with or without Z)
        val cleaned = isoString.replace("Z", "").trim()
        val dateTime = LocalDateTime.parse(cleaned)
        dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))
    } catch (e: Exception) {
        android.util.Log.e("formatDateTime", "Failed to parse: $isoString", e)
        isoString
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel = hiltViewModel(),
    onRecordClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val pendingTasks = viewModel.pendingTasks.collectAsState()
    val completedTasks = viewModel.completedTasks.collectAsState()
    val isRefreshing = viewModel.isRefreshing.collectAsState()
    val error = viewModel.error.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VoiceTask") },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.signOut()
                            onSignOut()
                        }
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign out")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRecordClick) {
                Icon(Icons.Default.Add, contentDescription = "Record task")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing.value,
            onRefresh = { viewModel.refreshTasks() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (pendingTasks.value.isEmpty() && completedTasks.value.isEmpty() && !isRefreshing.value) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Tap + to record your first task",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 8.dp)
                ) {
                    item {
                        TaskCountHeader(
                            pendingCount = pendingTasks.value.size,
                            completedCount = completedTasks.value.size,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }

                    if (pendingTasks.value.isNotEmpty()) {
                        item {
                            TaskSectionHeader("Pending", pendingTasks.value.size)
                        }
                        items(pendingTasks.value) { task ->
                            TaskCard(
                                task = task,
                                onClick = { onTaskClick(task.id) }
                            )
                        }
                    }

                    if (completedTasks.value.isNotEmpty()) {
                        item {
                            TaskSectionHeader("Completed", completedTasks.value.size)
                        }
                        items(completedTasks.value) { task ->
                            TaskCard(
                                task = task,
                                onClick = { onTaskClick(task.id) }
                            )
                        }
                    }
                }
            }

            if (error.value != null) {
                Text(
                    text = "Error: ${error.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
fun TaskCountHeader(
    pendingCount: Int,
    completedCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = "Pending ($pendingCount) • Completed ($completedCount)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun TaskSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit
) {
    val statusColor = when (task.status) {
        "PENDING" -> MaterialTheme.colorScheme.tertiary
        "COMPLETED" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )

                StatusBadge(
                    status = task.status,
                    backgroundColor = statusColor
                )
            }

            if (!task.description.isNullOrEmpty()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Priority: ${task.priority}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            val displayTime = task.dueAtUser ?: task.dueAt
            android.util.Log.d("TaskCard", "Task: ${task.title}, dueAtUser=${task.dueAtUser}, dueAt=${task.dueAt}, displayTime=$displayTime")
            if (displayTime != null) {
                Text(
                    text = "Due: ${formatDateTime(displayTime)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .background(
                color = backgroundColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = backgroundColor
        )
    }
}
