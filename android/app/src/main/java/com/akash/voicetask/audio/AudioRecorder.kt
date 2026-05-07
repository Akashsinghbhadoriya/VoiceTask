package com.akash.voicetask.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private val TAG = "AudioRecorder"

    private var onRecordingComplete: (() -> Unit)? = null
    private var onRecordingError: ((String) -> Unit)? = null

    fun startRecording(onComplete: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        try {
            this.onRecordingComplete = onComplete
            this.onRecordingError = onError

            // Create output file
            val cacheDir = context.cacheDir
            currentFile = File(cacheDir, "recording_${System.currentTimeMillis()}.m4a")
            Log.d(TAG, "Starting recording to: ${currentFile?.absolutePath}")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setAudioEncodingBitRate(32000)
                setOutputFile(currentFile!!.absolutePath)
                prepare()
                start()
            }

            Log.d(TAG, "Recording started successfully")

            // Auto-stop after 60 seconds
            stopHandler.postDelayed({
                if (mediaRecorder != null) {
                    stopRecording()
                }
            }, 60000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            onRecordingError?.invoke(e.message ?: "Failed to start recording")
        }
    }

    fun stopRecording() {
        try {
            stopHandler.removeCallbacksAndMessages(null)

            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            val fileSize = currentFile?.length() ?: 0
            Log.d(TAG, "Recording stopped. File size: $fileSize bytes (${fileSize / 1024} KB)")

            onRecordingComplete?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            onRecordingError?.invoke(e.message ?: "Failed to stop recording")
            currentFile?.delete()
            currentFile = null
        }
    }

    fun cancelRecording() {
        try {
            stopHandler.removeCallbacksAndMessages(null)

            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            currentFile?.delete()
            currentFile = null
        } catch (e: Exception) {
            // Silently fail
        }
    }

    fun getCurrentFile(): File? = currentFile

    fun deleteCurrentFile() {
        currentFile?.delete()
        currentFile = null
    }

    fun cleanupOrphanedFiles() {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("recording_") && file.name.endsWith(".m4a")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }
}
