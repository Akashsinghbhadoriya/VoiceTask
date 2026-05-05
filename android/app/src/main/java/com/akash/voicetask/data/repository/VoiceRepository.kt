package com.akash.voicetask.data.repository

import com.akash.voicetask.data.remote.ApiService
import com.akash.voicetask.data.remote.ExtractRequest
import com.akash.voicetask.domain.model.ExtractedTask
import com.akash.voicetask.domain.model.TranscribeResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class VoiceRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun transcribeAudio(audioFile: File): String {
        val requestBody = audioFile.asRequestBody("audio/mp4".toMediaType())
        val part = MultipartBody.Part.createFormData("audio", audioFile.name, requestBody)
        val response: TranscribeResponse = apiService.transcribeAudio(part)
        return response.transcript
    }

    suspend fun extractTask(text: String, timezone: String): ExtractedTask {
        return apiService.extractTask(ExtractRequest(text, timezone))
    }
}
