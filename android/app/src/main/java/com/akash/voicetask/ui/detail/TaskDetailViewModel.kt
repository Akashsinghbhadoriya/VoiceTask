package com.akash.voicetask.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.voicetask.data.repository.TaskRepository
import com.akash.voicetask.domain.model.Task
import com.akash.voicetask.domain.model.TaskRequest
import com.akash.voicetask.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _task = MutableStateFlow<UiState<Task>>(UiState.Idle)
    val task: StateFlow<UiState<Task>> = _task.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadTask(taskId: String) {
        viewModelScope.launch {
            try {
                _task.value = UiState.Loading
                val loadedTask = taskRepository.getTask(taskId)
                if (loadedTask != null) {
                    _task.value = UiState.Success(loadedTask)
                } else {
                    _error.value = "Task not found"
                    _task.value = UiState.Error("Task not found")
                }
            } catch (e: Exception) {
                _error.value = e.message
                _task.value = UiState.Error(e.message ?: "Error loading task")
            }
        }
    }

    fun toggleEditMode() {
        _isEditing.value = !_isEditing.value
    }

    fun updateTask(updatedTask: Task) {
        viewModelScope.launch {
            try {
                _isSaving.value = true
                _error.value = null

                val request = TaskRequest(
                    title = updatedTask.title,
                    description = updatedTask.description,
                    dueAt = updatedTask.dueAt,
                    reminderOffsetMinutes = updatedTask.reminderOffsetMinutes,
                    priority = updatedTask.priority,
                    transcript = updatedTask.transcript
                )

                val result = taskRepository.updateTask(updatedTask.id, request)
                _task.value = UiState.Success(result)
                _isEditing.value = false
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun markTaskComplete(taskId: String) {
        viewModelScope.launch {
            try {
                val currentTask = (task.value as? UiState.Success)?.data ?: return@launch
                val updated = currentTask.copy(status = "COMPLETED")

                val request = TaskRequest(
                    title = updated.title,
                    description = updated.description,
                    dueAt = updated.dueAt,
                    reminderOffsetMinutes = updated.reminderOffsetMinutes,
                    priority = updated.priority,
                    transcript = updated.transcript
                )

                taskRepository.updateTask(taskId, request)
                _task.value = UiState.Success(updated)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                _isSaving.value = true
                taskRepository.deleteTask(taskId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
