package com.helpmeread.app.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.helpmeread.app.data.HistoryItem
import com.helpmeread.app.data.HistoryManager

@Composable
fun HistoryScreen(
    historyManager: HistoryManager,
    onBack: () -> Unit,
    onReadAloud: (String) -> Unit
) {
    val view = LocalView.current
    var items by remember { mutableStateOf(historyManager.getItems()) }
    var showClearDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .systemBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1E))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "History",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )

                if (items.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1E))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showClearDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear All",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No saved texts yet",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Read text and tap Save to keep it here",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        HistoryItemCard(
                            item = item,
                            onRead = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onReadAloud(item.text)
                            },
                            onDelete = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                historyManager.deleteItem(item.id)
                                items = historyManager.getItems()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All History") },
            text = { Text("Delete all saved texts? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyManager.clearAll()
                        items = emptyList()
                        showClearDialog = false
                    }
                ) {
                    Text("Delete All", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryItemCard(
    item: HistoryItem,
    onRead: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1C1E)
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onRead)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = HistoryManager.formatTimestamp(item.timestamp),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.text,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${item.blockCount} text areas • Tap to read aloud",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
