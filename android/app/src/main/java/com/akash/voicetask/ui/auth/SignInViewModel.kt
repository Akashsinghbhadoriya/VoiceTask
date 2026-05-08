package com.akash.voicetask.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.voicetask.data.remote.ApiService
import com.akash.voicetask.data.remote.DeviceRequest
import com.akash.voicetask.data.remote.UserUpdateRequest
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    internal val client: SupabaseClient,
    private val apiService: ApiService
) : ViewModel() {

    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Observe sessionStatus flow — whenever auth state changes (sign-in, sign-out, refresh)
        // re-read currentSessionOrNull() so the UI reacts without needing to know SessionStatus types
        viewModelScope.launch {
            client.auth.sessionStatus.collect {
                val newSession = client.auth.currentSessionOrNull()
                _session.value = newSession
                if (newSession != null) {
                    try {
                        val metadata = client.auth.currentUserOrNull()?.userMetadata
                        val name = metadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                        val picture = metadata?.get("picture")?.jsonPrimitive?.contentOrNull
                        apiService.createOrUpdateUser(UserUpdateRequest(name = name, picture = picture))
                    } catch (_: Exception) {
                        // Non-fatal: backend user sync failed, task creation will retry on next sign-in
                    }
                    try {
                        val token = FirebaseMessaging.getInstance().token.await()
                        apiService.registerDevice(DeviceRequest(fcmToken = token, platform = "android"))
                    } catch (_: Exception) {
                        // Non-fatal: device registration failed, will retry on next token refresh
                    }
                }
            }
        }
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
}
