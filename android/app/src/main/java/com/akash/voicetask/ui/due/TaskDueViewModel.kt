package com.akash.voicetask.ui.due

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.voicetask.audio.AudioRecorder
import com.akash.voicetask.data.remote.ApiService
import com.akash.voicetask.domain.model.Task
import com.akash.voicetask.domain.model.UpdateTaskStatusRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class TaskDueViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Listening : UiState()
        object Processing : UiState()
        data class Error(val message: String) : UiState()
        object Completed : UiState()
        data class Rescheduled(val task: Task) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var audioRecorder: AudioRecorder? = null

    companion object {
        private const val TAG = "TaskDueViewModel"
    }

    fun startListening() {
        if (_uiState.value is UiState.Listening) return
        audioRecorder?.cancelRecording()
        audioRecorder = AudioRecorder(context)
        audioRecorder?.startRecording(
            onComplete = { /* recording stopped externally */ },
            onError = { msg ->
                _uiState.value = UiState.Error("Microphone error: $msg")
                audioRecorder = null
            }
        )
        _uiState.value = UiState.Listening
        Log.d(TAG, "Started listening for reschedule time")
    }

    fun stopListeningAndReschedule(taskId: String) {
        val recorder = audioRecorder ?: run {
            _uiState.value = UiState.Error("No recording in progress")
            return
        }

        recorder.stopRecording()
        val audioFile = recorder.getCurrentFile()
        audioRecorder = null

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            _uiState.value = UiState.Error("Recording was empty. Please try again.")
            return
        }

        _uiState.value = UiState.Processing
        uploadAndReschedule(taskId, audioFile)
    }

    private fun uploadAndReschedule(taskId: String, audioFile: File) {
        viewModelScope.launch {
            try {
                val timezone = TimeZone.getDefault().id
                val requestBody = audioFile.asRequestBody("audio/mp4".toMediaType())
                val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, requestBody)
                val timezonePart = timezone.toRequestBody("text/plain".toMediaType())

                val updatedTask = apiService.rescheduleTaskVoice(taskId, audioPart, timezonePart)
                audioFile.delete()

                Log.d(TAG, "Task $taskId rescheduled to ${updatedTask.dueAt}")
                _uiState.value = UiState.Rescheduled(updatedTask)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule task $taskId", e)
                audioFile.delete()
                val message = when {
                    e.message?.contains("could_not_parse_time") == true ->
                        "Could not understand the time. Please try again."
                    e.message?.contains("time_in_past") == true ->
                        "That time is in the past. Please say a future time."
                    else -> "Rescheduling failed. Please try again."
                }
                _uiState.value = UiState.Error(message)
            }
        }
    }

    fun markComplete(taskId: String) {
        _uiState.value = UiState.Processing
        viewModelScope.launch {
            try {
                apiService.updateTaskStatus(taskId, UpdateTaskStatusRequest(status = "COMPLETED"))
                Log.d(TAG, "Task $taskId marked complete")
                _uiState.value = UiState.Completed
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark task $taskId complete", e)
                _uiState.value = UiState.Error("Failed to mark complete. Please try again.")
            }
        }
    }

    fun retryFromError() {
        _uiState.value = UiState.Idle
    }

    fun cancelListening() {
        audioRecorder?.cancelRecording()
        audioRecorder = null
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        audioRecorder?.cancelRecording()
        super.onCleared()
    }
}
