package com.akash.voicetask.data.remote

import com.akash.voicetask.domain.model.ExtractedTask
import com.akash.voicetask.domain.model.Task
import com.akash.voicetask.domain.model.TaskRequest
import com.akash.voicetask.domain.model.TranscribeResponse
import com.akash.voicetask.domain.model.UpdateTaskStatusRequest
import com.akash.voicetask.domain.model.UserSession
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.Serializable

@Serializable
data class UserUpdateRequest(
    val name: String? = null,
    val picture: String? = null,
    val timezone: String? = null
)

@Serializable
data class ExtractRequest(
    val text: String,
    val timezone: String
)

@Serializable
data class DeviceRequest(
    val fcmToken: String,
    val platform: String
)

interface ApiService {
    // Users
    @POST("users/me")
    suspend fun createOrUpdateUser(
        @Body request: UserUpdateRequest
    ): UserSession

    @PATCH("users/me")
    suspend fun updateUser(
        @Body request: UserUpdateRequest
    ): UserSession

    // Voice
    @Multipart
    @POST("voice/transcribe")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part
    ): TranscribeResponse

    @POST("voice/extract")
    suspend fun extractTask(
        @Body request: ExtractRequest
    ): ExtractedTask

    // Tasks
    @GET("tasks")
    suspend fun getTasks(
        @Query("status") status: String? = null
    ): List<Task>

    @GET("tasks/{id}")
    suspend fun getTask(
        @Path("id") taskId: String
    ): Task

    @POST("tasks")
    suspend fun createTask(
        @Body request: TaskRequest
    ): Task

    @PATCH("tasks/{id}")
    suspend fun updateTask(
        @Path("id") taskId: String,
        @Body request: TaskRequest
    ): Task

    @PATCH("tasks/{id}")
    suspend fun updateTaskStatus(
        @Path("id") taskId: String,
        @Body request: UpdateTaskStatusRequest
    ): Task

    @DELETE("tasks/{id}")
    suspend fun deleteTask(
        @Path("id") taskId: String
    )

    @Multipart
    @POST("tasks/{id}/reschedule-voice")
    suspend fun rescheduleTaskVoice(
        @Path("id") taskId: String,
        @Part audio: MultipartBody.Part,
        @Part("timezone") timezone: RequestBody
    ): Task

    // Devices
    @POST("devices")
    suspend fun registerDevice(
        @Body request: DeviceRequest
    )

    @DELETE("devices/{fcmToken}")
    suspend fun unregisterDevice(
        @Path("fcmToken") fcmToken: String
    )
}
