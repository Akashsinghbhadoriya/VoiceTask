package com.akash.voicetask.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val dueAt: String?,
    val reminderOffsetMinutes: Int,
    val priority: String,
    val status: String,
    val transcript: String?,
    val createdAt: String,
    val updatedAt: String
)
