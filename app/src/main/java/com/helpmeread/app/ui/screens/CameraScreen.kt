package com.helpmeread.app.ui.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.helpmeread.app.data.TextBlock
import com.helpmeread.app.services.RealtimeOcrAnalyzer
import com.helpmeread.app.ui.components.LanguageSelector
import com.helpmeread.app.ui.components.VoiceSettings
import com.helpmeread.app.viewmodel.AppViewModel
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var showLanguageSelector by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var liveOcrEnabled by remember { mutableStateOf(false) }
    var liveBlocks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }
    var liveImageSize by remember { mutableStateOf(IntSize.Zero) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    
    val ocrAnalyzer = remember {
        RealtimeOcrAnalyzer { blocks, width, height ->
            liveBlocks = blocks
            liveImageSize = IntSize(width, height)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            ocrAnalyzer.close()
            executor.shutdown()
        }
    }
    
    // ML Kit Document Scanner — edge detect + crop + deskew before OCR
    val docScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uri = scan?.pages?.firstOrNull()?.imageUri
            if (uri != null) {
                copyUriToCache(context, uri)?.let { viewModel.processImage(it) }
            } else {
                Toast.makeText(context, "No page scanned", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val ttsVolume by viewModel.ttsVolume.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val ttsReady by viewModel.ttsReady.collectAsState()
    
    val captureScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "capture_scale"
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        key(liveOcrEnabled) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                            .build()
                        
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        
                        cameraProvider.unbindAll()
                        if (liveOcrEnabled) {
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(executor, ocrAnalyzer)
                                }
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                        } else {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Use case binding failed", e)
                        Toast.makeText(ctx, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        }
        
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.Center)
                .border(4.dp, Color(0xFF4CAF50), RoundedCornerShape(20.dp))
                .background(Color.Transparent)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd
                ).forEach { alignment ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(alignment)
                            .offset(
                                x = if (alignment == Alignment.TopEnd || alignment == Alignment.BottomEnd) 4.dp else (-4).dp,
                                y = if (alignment == Alignment.BottomStart || alignment == Alignment.BottomEnd) 4.dp else (-4).dp
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF4CAF50))
                    )
                }
            }
        }
        
        // Real-time OCR overlay
        if (liveOcrEnabled && liveBlocks.isNotEmpty() && liveImageSize.width > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        previewSize = coordinates.size
                    }
            ) {
                if (previewSize.width > 0 && previewSize.height > 0) {
                    // The camera preview fills the screen, so we scale from image to preview
                    val scaleX = liveImageSize.width.toFloat() / size.width
                    val scaleY = liveImageSize.height.toFloat() / size.height
                    val scale = minOf(scaleX, scaleY)
                    
                    val scaledWidth = liveImageSize.width / scale
                    val scaledHeight = liveImageSize.height / scale
                    val offsetX = (size.width - scaledWidth) / 2
                    val offsetY = (size.height - scaledHeight) / 2
                    
                    liveBlocks.forEach { block ->
                        val rect = block.boundingBox
                        val left = offsetX + rect.left / scale
                        val top = offsetY + rect.top / scale
                        val right = offsetX + rect.right / scale
                        val bottom = offsetY + rect.bottom / scale
                        
                        drawRoundRect(
                            color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawRoundRect(
                            color = Color(0xFF4CAF50),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    "Point camera at text",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        // History button (top-left)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewModel.showHistory()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "History",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live OCR toggle
            Surface(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        liveOcrEnabled = !liveOcrEnabled
                        if (!liveOcrEnabled) {
                            liveBlocks = emptyList()
                        }
                    },
                color = if (liveOcrEnabled) Color(0xFF4CAF50).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (liveOcrEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (liveOcrEnabled) {
                            if (liveBlocks.isNotEmpty()) "Live: ${liveBlocks.size} texts" else "Live OCR ON"
                        } else "Live OCR",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showLanguageSelector = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedLanguage.flag,
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (flashEnabled) Color(0xFFFFC107).copy(alpha = 0.8f)
                            else Color.Black.copy(alpha = 0.6f)
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            flashEnabled = !flashEnabled
                            imageCapture?.flashMode = if (flashEnabled) 
                                ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showVoiceSettings = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            startDocumentScan(context, docScanLauncher::launch)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Scan document",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(captureScale)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(6.dp, Color(0xFF4CAF50), CircleShape)
                    .clickable(enabled = !isCapturing) {
                        if (ttsReady) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            isCapturing = true
                            captureImage(imageCapture, executor, context, viewModel) {
                                isCapturing = false
                            }
                        } else {
                            Toast
                                .makeText(context, "Please wait...", Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) Color.Gray else Color(0xFF4CAF50))
                )
            }
        }
    }
    
    if (showLanguageSelector) {
        LanguageSelector(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { 
                viewModel.setLanguage(it)
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
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
            onTest = { 
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.speakTest()
            }
        )
    }
}

private fun captureImage(
    imageCapture: ImageCapture?,
    executor: java.util.concurrent.ExecutorService,
    context: Context,
    viewModel: AppViewModel,
    onComplete: () -> Unit
) {
    val capture = imageCapture ?: run {
        onComplete()
        return
    }
    
    val photoFile = File(
        context.cacheDir,
        "temp_image_${System.currentTimeMillis()}.jpg"
    )
    
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    capture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            }
            
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    viewModel.processImage(photoFile)
                    onComplete()
                }
            }
        }
    )
}

private fun startDocumentScan(
    context: Context,
    launch: (IntentSenderRequest) -> Unit
) {
    val activity = context as? Activity ?: run {
        Toast.makeText(context, "Scanner unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val options = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setGalleryImportAllowed(true)
        .setPageLimit(1)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .build()
    
    GmsDocumentScanning.getClient(options)
        .getStartScanIntent(activity)
        .addOnSuccessListener { sender ->
            launch(IntentSenderRequest.Builder(sender).build())
        }
        .addOnFailureListener { e ->
            Log.e("CameraScreen", "Document scanner unavailable", e)
            Toast.makeText(
                context,
                "Document scanner requires Google Play Services",
                Toast.LENGTH_SHORT
            ).show()
        }
}

private fun copyUriToCache(context: Context, uri: Uri): File? {
    return try {
        val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(file).use { input.copyTo(it) }
        } ?: return null
        file
    } catch (e: Exception) {
        Log.e("CameraScreen", "Failed to copy scanned page", e)
        null
    }
}
