package com.akash.voicetask.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.akash.voicetask.ui.auth.SignInScreen
import com.akash.voicetask.ui.auth.SignInViewModel
import com.akash.voicetask.ui.home.TaskListScreen
import com.akash.voicetask.ui.record.PreviewScreen
import com.akash.voicetask.ui.record.RecordScreen
import com.akash.voicetask.ui.record.RecordViewModel
import com.akash.voicetask.ui.record.TranscriptScreen
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import android.net.Uri

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: SignInViewModel = hiltViewModel()
    val session = viewModel.session.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (session.value != null) "home" else "sign_in"
    ) {
        composable("sign_in") {
            SignInScreen(
                viewModel = viewModel,
                onSignInSuccess = {
                    navController.navigate("home") {
                        popUpTo("sign_in") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            TaskListScreen(
                onRecordClick = {
                    navController.navigate("record")
                },
                onTaskClick = { taskId ->
                    navController.navigate("task_detail/$taskId")
                },
                onSignOut = {
                    navController.navigate("sign_in") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable("record") {
            RecordScreen(
                onNavigateToTranscript = { transcript ->
                    navController.navigate("transcript/${Uri.encode(transcript)}")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("transcript/{transcript}") { backStackEntry ->
            val transcript = backStackEntry.arguments?.getString("transcript") ?: ""
            val recordBackStackEntry = remember(navController) {
                navController.getBackStackEntry("record")
            }
            val recordViewModel: RecordViewModel = hiltViewModel(recordBackStackEntry)
            TranscriptScreen(
                transcript = transcript,
                viewModel = recordViewModel,
                onNavigateToPreview = { editedTranscript, timezone ->
                    navController.navigate("preview/${Uri.encode(editedTranscript)}/${Uri.encode(timezone)}")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("preview/{transcript}/{timezone}") { backStackEntry ->
            val transcript = backStackEntry.arguments?.getString("transcript") ?: ""
            val recordBackStackEntry = remember(navController) {
                navController.getBackStackEntry("record")
            }
            val recordViewModel: RecordViewModel = hiltViewModel(recordBackStackEntry)
            val recordStep = recordViewModel.recordStep.collectAsState()

            val extracted = (recordStep.value as? RecordViewModel.RecordStep.Preview)?.extracted
                ?: com.akash.voicetask.domain.model.ExtractedTask(
                    title = transcript,
                    description = null,
                    dueAt = null,
                    reminderOffsetMinutes = 15,
                    priority = "medium"
                )

            PreviewScreen(
                transcript = transcript,
                extracted = extracted,
                onTaskSaved = {
                    navController.navigate("home") {
                        popUpTo("preview/{transcript}/{timezone}") { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("task_detail/{taskId}") { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
            com.akash.voicetask.ui.detail.TaskDetailScreen(
                taskId = taskId,
                onNavigateBack = { navController.popBackStack() },
                onTaskDeleted = {
                    navController.navigate("home") {
                        popUpTo("task_detail/{taskId}") { inclusive = true }
                    }
                }
            )
        }
    }
}
