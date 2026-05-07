package com.akash.voicetask.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.voicetask.data.repository.TaskRepository
import com.akash.voicetask.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _pendingTasks = MutableStateFlow<List<Task>>(emptyList())
    val pendingTasks: StateFlow<List<Task>> = _pendingTasks.asStateFlow()

    private val _completedTasks = MutableStateFlow<List<Task>>(emptyList())
    val completedTasks: StateFlow<List<Task>> = _completedTasks.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            try {
                // First, try to refresh from API to populate local DB
                taskRepository.refreshTasks()
            } catch (e: Exception) {
                _error.value = e.message
            }

            // Then observe local DB for changes
            taskRepository.getAllTasks().collect { tasks ->
                val sorted = tasks.sortedWith(
                    compareBy<Task> { it.dueAt == null }
                        .thenBy { it.dueAt }
                )
                _pendingTasks.value = sorted.filter { it.status == "PENDING" }
                _completedTasks.value = sorted.filter { it.status == "COMPLETED" }
            }
        }
    }

    fun refreshTasks() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                taskRepository.refreshTasks()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                taskRepository.deleteTask(taskId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    suspend fun signOut() {
        try {
            supabaseClient.auth.signOut()
        } catch (e: Exception) {
            _error.value = e.message
        }
    }

    fun clearError() {
        _error.value = null
    }
}
