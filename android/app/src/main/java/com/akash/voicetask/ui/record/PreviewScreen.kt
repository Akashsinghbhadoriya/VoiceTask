package com.akash.voicetask.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akash.voicetask.domain.model.ExtractedTask
import com.akash.voicetask.domain.model.TaskRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    transcript: String,
    extracted: ExtractedTask,
    viewModel: PreviewViewModel = hiltViewModel(),
    onTaskSaved: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isSaving = viewModel.isSaving.collectAsState()
    val saveError = viewModel.error.collectAsState()

    var title by remember { mutableStateOf(extracted.title) }
    var description by remember { mutableStateOf(extracted.description ?: "") }
    var dueAtStr by remember { mutableStateOf(extracted.dueAt ?: "") }
    var reminderOffset by remember { mutableStateOf(extracted.reminderOffsetMinutes) }
    var priority by remember { mutableStateOf(extracted.priority.uppercase()) }

    var priorityExpanded by remember { mutableStateOf(false) }
    var reminderExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("Preview Task") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            TextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            TextField(
                value = dueAtStr,
                onValueChange = { dueAtStr = it },
                label = { Text("Due Date & Time (ISO 8601)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

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

            if (saveError.value != null) {
                Text(
                    text = "Error: ${saveError.value ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val request = TaskRequest(
                            title = title,
                            description = description.takeIf { it.isNotEmpty() },
                            dueAt = dueAtStr.takeIf { it.isNotEmpty() },
                            reminderOffsetMinutes = reminderOffset,
                            priority = priority,
                            transcript = transcript
                        )
                        viewModel.saveTask(request) { onTaskSaved() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving.value && title.isNotEmpty()
                ) {
                    Text(if (isSaving.value) "Saving..." else "Save Task")
                }

                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving.value
                ) {
                    Text("Back")
                }
            }
        }
    }
}
