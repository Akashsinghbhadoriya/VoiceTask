package com.akash.voicetask.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akash.voicetask.domain.model.Task
import com.akash.voicetask.domain.model.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    viewModel: TaskDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onTaskDeleted: () -> Unit
) {
    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    val task = viewModel.task.collectAsState()
    val isEditing = viewModel.isEditing.collectAsState()
    val isSaving = viewModel.isSaving.collectAsState()
    val error = viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("Task Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isEditing.value) {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = task.value) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }

            is UiState.Success -> {
                TaskDetailContent(
                    task = state.data,
                    isEditing = isEditing.value,
                    isSaving = isSaving.value,
                    error = error.value,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onToggleEdit = { viewModel.toggleEditMode() },
                    onSave = { updated -> viewModel.updateTask(updated) },
                    onMarkComplete = { viewModel.markTaskComplete(taskId) },
                    onDelete = {
                        viewModel.deleteTask(taskId)
                        onTaskDeleted()
                    },
                    onClearError = { viewModel.clearError() }
                )
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = onNavigateBack) { Text("Back") }
                }
            }

            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailContent(
    task: Task,
    isEditing: Boolean,
    isSaving: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    onToggleEdit: () -> Unit,
    onSave: (Task) -> Unit,
    onMarkComplete: () -> Unit,
    onDelete: () -> Unit,
    onClearError: () -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var dueAtStr by remember { mutableStateOf(task.dueAt ?: "") }
    var reminderOffset by remember { mutableStateOf(task.reminderOffsetMinutes) }
    var priority by remember { mutableStateOf(task.priority.uppercase()) }

    var priorityExpanded by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = !isEditing,
            enabled = isEditing
        )

        TextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            readOnly = !isEditing,
            enabled = isEditing
        )

        TextField(
            value = dueAtStr,
            onValueChange = { dueAtStr = it },
            label = { Text("Due Date & Time") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = !isEditing,
            enabled = isEditing
        )

        if (isEditing) {
            ExposedDropdownMenuBox(
                expanded = reminderExpanded,
                onExpandedChange = { reminderExpanded = it }
            ) {
                TextField(
                    value = "$reminderOffset min",
                    onValueChange = {},
                    label = { Text("Reminder") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderExpanded)
                    }
                )

                ExposedDropdownMenu(
                    expanded = reminderExpanded,
                    onDismissRequest = { reminderExpanded = false }
                ) {
                    listOf(5, 15, 30, 60, 1440).forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text("$minutes min") },
                            onClick = {
                                reminderOffset = minutes
                                reminderExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = priorityExpanded,
                onExpandedChange = { priorityExpanded = it }
            ) {
                TextField(
                    value = priority,
                    onValueChange = {},
                    label = { Text("Priority") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded)
                    }
                )

                ExposedDropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { priorityExpanded = false }
                ) {
                    listOf("LOW", "MEDIUM", "HIGH").forEach { pri ->
                        DropdownMenuItem(
                            text = { Text(pri) },
                            onClick = {
                                priority = pri
                                priorityExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Text(
            text = "Status: ${task.status}",
            style = MaterialTheme.typography.labelSmall
        )

        if (!task.transcript.isNullOrEmpty()) {
            Text(
                text = "Transcript",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = task.transcript,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (error != null) {
            Text(
                text = "Error: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isEditing) {
                Button(
                    onClick = {
                        onSave(
                            task.copy(
                                title = title,
                                description = description.takeIf { it.isNotEmpty() },
                                dueAt = dueAtStr.takeIf { it.isNotEmpty() },
                                reminderOffsetMinutes = reminderOffset,
                                priority = priority
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && title.isNotEmpty()
                ) {
                    Text(if (isSaving) "Saving..." else "Save")
                }

                OutlinedButton(
                    onClick = onToggleEdit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            } else {
                if (task.status != "COMPLETED") {
                    Button(
                        onClick = onMarkComplete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mark Complete")
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Delete")
                }
            }
        }
    }
}
