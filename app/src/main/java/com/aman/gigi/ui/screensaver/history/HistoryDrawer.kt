package com.aman.gigi.ui.screensaver.history

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.aman.gigi.ui.screensaver.components.SafeGlassBox
import com.aman.gigi.viewmodel.ScreensaverViewModel

@Composable
fun HistoryDrawer(
    viewModel: ScreensaverViewModel,
    onClose: () -> Unit
) {
    val isDark = false
    val historyItems by viewModel.historyFlow.collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount > 20f) onClose()
                }
            }
            .padding(start = 64.dp) // Leave space on the LEFT to tap out
    ) {
        // Frosted Glass Effect
        SafeGlassBox(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp),
            borderWidth = 0.dp
        ) {
            Surface(
                color = if (isDark) Color(0xFF1A1A2E).copy(alpha = 0.97f) else Color(0xFFE6E0FF).copy(alpha = 0.96f),
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Our Story",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5CF6)
                            )
                        }

                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8B5CF6))
                        }
                    }

                    HorizontalDivider(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Clear History Button
                    if (historyItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFD32F2F)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Memories", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // List
                    if (historyItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No memories yet ✨",
                                color = if (isDark) Color(0xFFB0A8D0) else Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(
                            reverseLayout = true, // Newest at bottom like chat
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(historyItems) { item ->
                                CuteHistoryItem(
                                    item = item,
                                    onClick = {
                                        viewModel.replayScribble(item.scribbleId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
