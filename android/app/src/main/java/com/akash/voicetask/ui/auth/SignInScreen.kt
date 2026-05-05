package com.akash.voicetask.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google

@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onSignInSuccess: () -> Unit
) {
    val loading = viewModel.loading.collectAsState()
    val error = viewModel.error.collectAsState()
    val session = viewModel.session.collectAsState()

    // Set up Google Sign-In with fallback to web OAuth when Credential Manager fails
    val googleSignInState = viewModel.client.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            when (result) {
                NativeSignInResult.Success -> viewModel.clearError()
                is NativeSignInResult.Error -> viewModel.setError("Sign-in failed: ${result.message}")
                is NativeSignInResult.ClosedByUser -> {} // User canceled
                is NativeSignInResult.NetworkError -> viewModel.setError("Network error during sign-in")
            }
        },
        fallback = {
            // Fallback to web OAuth flow when native Credential Manager fails (e.g., emulator error 28444)
            viewModel.client.auth.signInWith(Google, redirectUrl = "voicetask://auth-callback")
        }
    )

    // Check if already signed in
    LaunchedEffect(session.value) {
        if (session.value != null) {
            onSignInSuccess()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "VoiceTask",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Convert your voice to tasks",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (error.value != null) {
                Text(
                    text = error.value ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { googleSignInState.startFlow() },
                modifier = Modifier.padding(top = 32.dp),
                enabled = !loading.value
            ) {
                if (loading.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text("Sign in with Google")
            }

            Text(
                text = "Powered by Supabase Auth",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp)
            )
        }
    }
}
