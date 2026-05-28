package com.akash.voicetask.notification

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.akash.voicetask.data.remote.ApiService
import com.akash.voicetask.domain.model.UpdateTaskStatusRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MarkTaskCompleteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_TASK_ID = "taskId"
        private const val TAG = "MarkTaskCompleteWorker"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()

        return try {
            apiService.updateTaskStatus(taskId, UpdateTaskStatusRequest(status = "COMPLETED"))
            Log.d(TAG, "Task $taskId marked COMPLETED")

            // Stop ringtone if still playing
            applicationContext.startService(
                Intent(applicationContext, RingtoneService::class.java).apply {
                    action = RingtoneService.ACTION_STOP
                }
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark task $taskId complete", e)
            Result.retry()
        }
    }
}
