package com.akash.voicetask.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val userId: String,
    val title: String,
    val description: String? = null,
    val dueAt: String? = null,
    val reminderOffsetMinutes: Int = 15,
    val priority: String = "MEDIUM",
    val status: String = "PENDING",
    val transcript: String? = null,
    val createdAt: String,
    val updatedAt: String
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
