package com.helpmeread.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.helpmeread.app.data.RecognizedText
import com.helpmeread.app.data.TextBlock
import com.helpmeread.app.services.VoiceCommand
import com.helpmeread.app.ui.components.LanguageSelector
import com.helpmeread.app.ui.components.VoiceSettings
import com.helpmeread.app.viewmodel.AppViewModel
import java.io.File
import kotlin.math.max

@Composable
fun ResultScreen(viewModel: AppViewModel, recognizedText: RecognizedText) {
    val imageFile = remember(recognizedText.imagePath) { File(recognizedText.imagePath) }
    val bitmap = remember(imageFile) {
        BitmapFactory.decodeFile(imageFile.absolutePath)?.let {
            val maxSize = 2048
            if (it.width > maxSize || it.height > maxSize) {
                val scale = maxSize.toFloat() / maxOf(it.width, it.height)
                Bitmap.createScaledBitmap(
                    it,
                    (it.width * scale).toInt(),
                    (it.height * scale).toInt(),
                    true
                )
            } else it
        }
    }
    
    var selectedBlockIndex by remember { mutableStateOf<Int?>(null) }
    var isAutoReading by remember { mutableStateOf(false) }
    var showLanguageSelector by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var isSaved by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    val view = LocalView.current
    val context = LocalContext.current
    val voiceAvailable by viewModel.voiceCommandManager.isAvailable.collectAsState()
    
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && voiceAvailable) {
            viewModel.voiceCommandManager.startListening()
            isListening = true
        } else {
            Toast.makeText(context, "Microphone permission needed for voice commands", Toast.LENGTH_SHORT).show()
        }
    }
    
    val voiceCommand by viewModel.voiceCommandManager.command.collectAsState()
    
    // Handle voice commands
    LaunchedEffect(voiceCommand) {
        when (voiceCommand) {
            null -> {}
            VoiceCommand.READ -> {
                isAutoReading = true
                viewModel.readAllBlocks(recognizedText.blocks) { isAutoReading = false }
            }
            VoiceCommand.STOP -> {
                viewModel.stopSpeaking()
                isAutoReading = false
            }
            VoiceCommand.SAVE -> {
                if (!isSaved) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.saveToHistory(recognizedText)
                    isSaved = true
                }
            }
            VoiceCommand.CAMERA -> viewModel.resetToCamera()
            VoiceCommand.BACK -> viewModel.resetToCamera()
            VoiceCommand.HISTORY -> viewModel.showHistory()
            VoiceCommand.NEXT -> {
                if (recognizedText.blocks.isNotEmpty()) {
                    val next = ((selectedBlockIndex ?: -1) + 1)
                        .coerceAtMost(recognizedText.blocks.lastIndex)
                    selectedBlockIndex = next
                    viewModel.speakTextBlock(recognizedText.blocks[next])
                }
            }
            VoiceCommand.UNKNOWN -> {}
        }
        if (voiceCommand != null) {
            viewModel.voiceCommandManager.clearCommand()
        }
    }
    
    // Stop voice commands when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.voiceCommandManager.stopListening()
        }
    }
    
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val ttsVolume by viewModel.ttsVolume.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val currentlySpeakingIndex by viewModel.currentlySpeakingIndex.collectAsState()
    
    LaunchedEffect(currentlySpeakingIndex) {
        selectedBlockIndex = currentlySpeakingIndex
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Image with overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            imageSize = coordinates.size
                        }
                        .pointerInput(recognizedText.blocks) {
                            detectTapGestures { offset ->
                                if (imageSize.width > 0 && imageSize.height > 0) {
                                    val scaleX = bitmap.width.toFloat() / imageSize.width
                                    val scaleY = bitmap.height.toFloat() / imageSize.height
                                    // ContentScale.Fit → image-px per view-px is the larger ratio
                                    val scale = max(scaleX, scaleY)
                                    
                                    val scaledWidth = bitmap.width / scale
                                    val scaledHeight = bitmap.height / scale
                                    val offsetX = (imageSize.width - scaledWidth) / 2
                                    val offsetY = (imageSize.height - scaledHeight) / 2
                                    
                                    // Bounding boxes live in OCR-bitmap space; convert tap to it
                                    val ocrPerDisplay = if (recognizedText.imageWidth > 0)
                                        recognizedText.imageWidth.toFloat() / bitmap.width else 1f
                                    val imageX = ((offset.x - offsetX) * scale * ocrPerDisplay).toInt()
                                    val imageY = ((offset.y - offsetY) * scale * ocrPerDisplay).toInt()
                                    
                                    recognizedText.blocks.forEachIndexed { index, block ->
                                        if (block.boundingBox.contains(imageX, imageY)) {
                                            selectedBlockIndex = index
                                            viewModel.speakTextBlock(block)
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    // Draw overlays
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (imageSize.width > 0 && imageSize.height > 0) {
                            val scaleX = bitmap.width.toFloat() / size.width
                            val scaleY = bitmap.height.toFloat() / size.height
                            // ContentScale.Fit → image-px per view-px is the larger ratio
                            val scale = max(scaleX, scaleY)
                            
                            val scaledWidth = bitmap.width / scale
                            val scaledHeight = bitmap.height / scale
                            val offsetX = (size.width - scaledWidth) / 2
                            val offsetY = (size.height - scaledHeight) / 2
                            
                            // Boxes are in OCR-bitmap space; scale into display-bitmap space first
                            val boxScale = if (recognizedText.imageWidth > 0)
                                bitmap.width.toFloat() / recognizedText.imageWidth else 1f
                            
                            recognizedText.blocks.forEachIndexed { index, block ->
                                val isSelected = selectedBlockIndex == index
                                val isSpeaking = currentlySpeakingIndex == index
                                
                                val rect = block.boundingBox
                                val left = offsetX + rect.left * boxScale / scale
                                val top = offsetY + rect.top * boxScale / scale
                                val right = offsetX + rect.right * boxScale / scale
                                val bottom = offsetY + rect.bottom * boxScale / scale
                                
                                val color = when {
                                    isSpeaking -> Color(0xFF2196F3)
                                    isSelected -> Color(0xFF4CAF50)
                                    else -> Color.Yellow.copy(alpha = 0.8f)
                                }
                                
                                // Fill
                                if (isSelected || isSpeaking) {
                                    drawRoundRect(
                                        color = color.copy(alpha = 0.3f),
                                        topLeft = Offset(left, top),
                                        size = Size(right - left, bottom - top),
                                        cornerRadius = CornerRadius(8f, 8f)
                                    )
                                }
                                
                                // Border
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                    cornerRadius = CornerRadius(8f, 8f),
                                    style = Stroke(width = if (isSelected || isSpeaking) 8f else 4f)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Top Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.resetToCamera() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showLanguageSelector = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedLanguage.flag,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            // Voice command toggle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isListening) Color(0xFFE53935) else Color.Black.copy(alpha = 0.6f))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (isListening) {
                            viewModel.voiceCommandManager.stopListening()
                            isListening = false
                        } else if (!voiceAvailable) {
                            Toast.makeText(context, "Voice recognition not available on this device", Toast.LENGTH_SHORT).show()
                        } else if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.voiceCommandManager.startListening()
                            isListening = true
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                    contentDescription = if (isListening) "Stop Listening" else "Voice Commands",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showVoiceSettings = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Voice command hint banner
        if (isListening) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                color = Color(0xFFE53935).copy(alpha = 0.9f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Listening... Say: Read, Stop, Save, Camera",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (recognizedText.blocks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (isAutoReading) Color(0xFFE53935) else Color(0xFF4CAF50))
                            .clickable {
                                if (isAutoReading) {
                                    viewModel.stopSpeaking()
                                    isAutoReading = false
                                } else {
                                    isAutoReading = true
                                    viewModel.readAllBlocks(recognizedText.blocks) {
                                        isAutoReading = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAutoReading) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isAutoReading) "Stop" else "Read All",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    if (currentlySpeakingIndex != null || isAutoReading) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF9800))
                                .clickable {
                                    viewModel.stopSpeaking()
                                    isAutoReading = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    // Save to History button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (isSaved) Color(0xFF4CAF50) else Color(0xFF2196F3))
                            .clickable {
                                if (!isSaved) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    viewModel.saveToHistory(recognizedText)
                                    isSaved = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Save,
                            contentDescription = if (isSaved) "Saved" else "Save",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "${recognizedText.blocks.size} text areas found",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                Text(
                    text = "No text found",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
    
    if (showLanguageSelector) {
        LanguageSelector(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { 
                viewModel.setLanguage(it)
                showLanguageSelector = false
            },
            onDismiss = { showLanguageSelector = false }
        )
    }
    
    if (showVoiceSettings) {
        VoiceSettings(
            volume = ttsVolume,
            speechRate = speechRate,
            onVolumeChange = { viewModel.setVolume(it) },
            onSpeechRateChange = { viewModel.setSpeechRate(it) },
            onDismiss = { showVoiceSettings = false },
            onTest = { viewModel.speakTest() }
        )
    }
}
