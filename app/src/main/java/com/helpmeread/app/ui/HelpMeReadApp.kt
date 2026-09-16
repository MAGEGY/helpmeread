package com.helpmeread.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.helpmeread.app.ui.screens.CameraScreen
import com.helpmeread.app.ui.screens.HistoryScreen
import com.helpmeread.app.ui.screens.ResultScreen
import com.helpmeread.app.ui.screens.SplashScreen
import com.helpmeread.app.viewmodel.AppState
import com.helpmeread.app.viewmodel.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun HelpMeReadApp() {
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel = remember { AppViewModel(context) }
    val appState by viewModel.appState.collectAsState()
    val ttsReady by viewModel.ttsReady.collectAsState()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    var showSplash by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        delay(2000)
        showSplash = false
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            showSplash -> SplashScreen()
            !hasCameraPermission -> PermissionScreen { 
                permissionLauncher.launch(Manifest.permission.CAMERA)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            else -> MainContent(viewModel, appState, ttsReady)
        }
    }
}

@Composable
fun MainContent(viewModel: AppViewModel, appState: AppState, ttsReady: Boolean) {
    when (appState) {
        is AppState.Camera -> CameraScreen(viewModel)
        is AppState.Processing -> ProcessingScreen()
        is AppState.Result -> ResultScreen(viewModel, appState.recognizedText)
        is AppState.Error -> ErrorScreen(viewModel, appState.message)
        is AppState.History -> HistoryScreen(
            historyManager = viewModel.historyManager,
            onBack = { viewModel.resetToCamera() },
            onReadAloud = { text -> viewModel.speakHistoryItem(text) }
        )
    }
    
    if (!ttsReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.padding(16.dp),
                color = Color(0xFFFF9800).copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "Initializing voice...",
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    val view = LocalView.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(80.dp)
            )
            
            Text(
                "Camera Permission Required",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                "This app needs camera access to read text for you",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onRequestPermission()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Camera, contentDescription = null)
                    Text("Allow Camera Access", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun ProcessingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp
            )
            
            Text(
                "Reading text...",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun ErrorScreen(viewModel: AppViewModel, message: String) {
    val view = LocalView.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(80.dp)
            )
            
            Text(
                "Oops!",
                color = Color(0xFFE53935),
                style = MaterialTheme.typography.headlineLarge
            )
            
            Text(
                message,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewModel.resetToCamera()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Try Again", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
