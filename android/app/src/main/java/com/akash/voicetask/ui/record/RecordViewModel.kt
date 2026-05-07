package com.akash.voicetask.ui.record

import androidx.lifecycle.ViewModel
import com.akash.voicetask.data.repository.VoiceRepository
import com.akash.voicetask.domain.model.ExtractedTask
import com.akash.voicetask.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import java.io.File
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    sealed class RecordStep {
        object Recording : RecordStep()
        object Transcribing : RecordStep()
        data class Transcript(val text: String) : RecordStep()
        data class Extracting(val transcript: String) : RecordStep()
        data class Preview(val transcript: String, val extracted: ExtractedTask) : RecordStep()
    }

    private val _recordStep = MutableStateFlow<RecordStep>(RecordStep.Recording)
    val recordStep: StateFlow<RecordStep> = _recordStep.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun transcribeAudio(audioFile: File, userTimezone: String = "Asia/Kolkata") {
        viewModelScope.launch {
            try {
                _recordStep.value = RecordStep.Transcribing
                val transcript = voiceRepository.transcribeAudio(audioFile)
                _recordStep.value = RecordStep.Transcript(transcript)
            } catch (e: Exception) {
                _error.value = e.message ?: "Transcription failed"
                _recordStep.value = RecordStep.Recording
            }
        }
    }

    fun extractTask(transcript: String, timezone: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                _recordStep.value = RecordStep.Extracting(transcript)
                val extracted = voiceRepository.extractTask(transcript, timezone)
                _recordStep.value = RecordStep.Preview(transcript, extracted)
                onSuccess?.invoke()
            } catch (e: Exception) {
                _error.value = e.message ?: "Extraction failed"
                _recordStep.value = RecordStep.Transcript(transcript)
            }
        }
    }

    fun retryTranscription(audioFile: File, timezone: String = "Asia/Kolkata") {
        _recordStep.value = RecordStep.Recording
        _error.value = null
        transcribeAudio(audioFile, timezone)
    }

    fun retryExtraction(transcript: String, timezone: String, onSuccess: (() -> Unit)? = null) {
        _error.value = null
        extractTask(transcript, timezone, onSuccess)
    }

    fun resetRecording() {
        _recordStep.value = RecordStep.Recording
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun setError(message: String) {
        _error.value = message
    }
}
