package com.akash.voicetask.data.repository

import com.akash.voicetask.data.local.TaskDao
import com.akash.voicetask.data.local.TaskEntity
import com.akash.voicetask.data.remote.ApiService
import com.akash.voicetask.domain.model.Task
import com.akash.voicetask.domain.model.TaskRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepository @Inject constructor(
    private val apiService: ApiService,
    private val taskDao: TaskDao
) {
    fun getAllTasks(): Flow<List<Task>> {
        return taskDao.getAllTasks().map { entities ->
            entities.map { it.toTask() }
        }
    }

    fun getTasksByStatus(status: String): Flow<List<Task>> {
        return taskDao.getTasksByStatus(status).map { entities ->
            entities.map { it.toTask() }
        }
    }

    suspend fun refreshTasks() {
        try {
            val tasks = apiService.getTasks()
            taskDao.deleteAllTasks()
            taskDao.insertTasks(tasks.map { it.toEntity() })
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getTask(taskId: String): Task? {
        return taskDao.getTaskById(taskId)?.toTask()
    }

    suspend fun createTask(request: TaskRequest): Task {
        val task = apiService.createTask(request)
        taskDao.insertTask(task.toEntity())
        return task
    }

    suspend fun updateTask(taskId: String, request: TaskRequest): Task {
        val task = apiService.updateTask(taskId, request)
        taskDao.updateTask(task.toEntity())
        return task
    }

    suspend fun deleteTask(taskId: String) {
        apiService.deleteTask(taskId)
        taskDao.deleteTaskById(taskId)
    }

    private fun TaskEntity.toTask(): Task {
        return Task(
            id = id,
            userId = userId,
            title = title,
            description = description,
            dueAt = dueAt,
            dueAtUser = dueAtUser,
            reminderOffsetMinutes = reminderOffsetMinutes,
            priority = priority,
            status = status,
            transcript = transcript,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = id,
            userId = userId,
            title = title,
            description = description,
            dueAt = dueAt,
            dueAtUser = dueAtUser,
            reminderOffsetMinutes = reminderOffsetMinutes,
            priority = priority,
            status = status,
            transcript = transcript,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
