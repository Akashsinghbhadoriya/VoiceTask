package com.akash.voicetask.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.voicetask.data.repository.TaskRepository
import com.akash.voicetask.domain.model.TaskRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun saveTask(request: TaskRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                taskRepository.createTask(request)
                _isSaving.value = false
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message
                _isSaving.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
