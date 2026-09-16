package com.helpmeread.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun VoiceSettings(
    volume: Float,
    speechRate: Float,
    onVolumeChange: (Float) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onTest: () -> Unit
) {
    val view = LocalView.current
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1C1C1E)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Text(
                        text = "Voice Settings",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Volume",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Slider(
                        value = volume,
                        onValueChange = {
                            onVolumeChange(it)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4CAF50),
                            activeTrackColor = Color(0xFF4CAF50),
                            inactiveTrackColor = Color(0xFF3A3A3C)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Silent",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Loud",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Speed",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Slider(
                        value = speechRate,
                        onValueChange = {
                            onSpeechRateChange(it)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                        valueRange = 0.5f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2196F3),
                            activeTrackColor = Color(0xFF2196F3),
                            inactiveTrackColor = Color(0xFF3A3A3C)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Slow",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = when {
                                speechRate < 0.8f -> "Slow"
                                speechRate > 1.3f -> "Fast"
                                else -> "Normal"
                            },
                            color = Color(0xFF2196F3),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Fast",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Test Voice",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
