package com.akash.voicetask.ui.record

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akash.voicetask.audio.AudioRecorder
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
import android.Manifest
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import androidx.compose.material.icons.filled.Info

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel = hiltViewModel(),
    onNavigateToTranscript: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val recordStep = viewModel.recordStep.collectAsState()
    val error = viewModel.error.collectAsState()
    val context = LocalContext.current

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val audioRecorder = remember {
        AudioRecorder(context).apply {
            cleanupOrphanedFiles()
        }
    }

    var isRecording by remember { mutableLongStateOf(0L) }
    var recordingTime by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val timer = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isRecording > 0) {
                    recordingTime += 100
                }
                timer.postDelayed(this, 100)
            }
        }

        timer.postDelayed(runnable, 100)

        onDispose {
            timer.removeCallbacks(runnable)
            audioRecorder.cancelRecording()
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text("Record Task") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val step = recordStep.value) {
                RecordViewModel.RecordStep.Recording -> {
                    if (micPermissionState.status == PermissionStatus.Granted) {
                        Text(
                            text = "Hold to Record",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                            text = "${recordingTime / 1000}s",
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .scale(if (isRecording > 0) 1.2f else 1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isRecording = System.currentTimeMillis()
                                            recordingTime = 0L
                                            audioRecorder.startRecording(
                                                onComplete = {
                                                    if (recordingTime >= 500) {
                                                        audioRecorder.getCurrentFile()?.let { file ->
                                                            viewModel.transcribeAudio(file)
                                                        }
                                                    } else {
                                                        viewModel.setError("Recording too short (min 0.5s)")
                                                    }
                                                    isRecording = 0L
                                                },
                                                onError = { error ->
                                                    viewModel.setError("Recording failed: $error")
                                                    isRecording = 0L
                                                }
                                            )
                                            tryAwaitRelease()
                                            if (isRecording > 0) {
                                                audioRecorder.stopRecording()
                                                isRecording = 0L
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Record",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        if (error.value != null) {
                            Text(
                                text = error.value ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Microphone Permission Required",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )

                        Text(
                            text = "Grant microphone access to record voice tasks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 32.dp)
                        )

                        Button(
                            onClick = { micPermissionState.launchPermissionRequest() }
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }

                RecordViewModel.RecordStep.Transcribing -> {
                    CircularProgressIndicator()
                    Text(
                        text = "Transcribing...",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                is RecordViewModel.RecordStep.Transcript -> {
                    LaunchedEffect(step) {
                        onNavigateToTranscript(step.text)
                    }
                }

                is RecordViewModel.RecordStep.Extracting -> {
                    CircularProgressIndicator()
                    Text(
                        text = "Extracting task...",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                is RecordViewModel.RecordStep.Preview -> {
                    LaunchedEffect(step) {
                        onNavigateToTranscript(step.transcript)
                    }
                }
            }
        }
    }
}
