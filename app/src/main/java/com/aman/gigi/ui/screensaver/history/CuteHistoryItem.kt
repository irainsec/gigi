package com.aman.gigi.ui.screensaver.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.model.ScribbleSummary
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Favorite

@Composable
fun CuteHistoryItem(
    item: ScribbleSummary,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSent = item.isSent
    val alignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart
    
    // Theme-consistent Colors
    val bubbleColor = if (isSent) {
        // Sent bubbles use the primary/secondary Indigo-Violet theme
        Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
    } else {
        // Received bubbles use the tertiary Pink theme
        Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFFF472B6)))
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
        ) {
            // Timestamp (Tiny & Cute)
            Text(
                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSent) Color(0xFF6366F1).copy(alpha = 0.5f) else Color(0xFFEC4899).copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )

            Row(verticalAlignment = Alignment.Bottom) {
                // The Bubble
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomStart = if (isSent) 20.dp else 4.dp,
                                bottomEnd = if (isSent) 4.dp else 20.dp
                            )
                        )
                        .background(bubbleColor)
                        .clickable(onClick = onClick) // Added clicking
                        .padding(12.dp)
                ) {
                    Column {
                        // Icon based on type
                        val (icon, label) = when (item.mediaType) {
                            "SPARKLE" -> Icons.Default.PhotoCamera to "Sparkle Sent"
                            "SCRIBBLE" -> Icons.Default.Brush to "Drawing"
                            "HEARTBEAT" -> Icons.Default.Favorite to "Heartbeat"
                            else -> Icons.Default.Brush to "Scribble"
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
