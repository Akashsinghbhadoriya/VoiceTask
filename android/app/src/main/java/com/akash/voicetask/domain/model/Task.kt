package com.akash.voicetask.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    val dueAtUser: String? = null,
    @SerialName("reminder_offset_minutes") val reminderOffsetMinutes: Int = 15,
    val priority: String = "MEDIUM",
    val status: String = "PENDING",
    val transcript: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("qstash_message_id") val qstashMessageId: String? = null
)

@Serializable
data class TaskRequest(
    val title: String,
    val description: String? = null,
    val dueAt: String? = null,
    val reminderOffsetMinutes: Int = 15,
    val priority: String = "MEDIUM",
    val transcript: String? = null
)

@Serializable
data class TranscribeResponse(
    val transcript: String
)

@Serializable
data class ExtractedTask(
    val title: String,
    val description: String? = null,
    val dueAt: String? = null,
    val dueAtUser: String? = null,
    val reminderOffsetMinutes: Int = 15,
    val priority: String = "medium"
)

data class UserSession(
    val id: String,
    val email: String,
    val name: String? = null,
    val picture: String? = null,
    val timezone: String = "Asia/Kolkata"
)

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
