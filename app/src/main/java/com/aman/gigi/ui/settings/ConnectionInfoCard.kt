package com.aman.gigi.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.model.Connection
import com.aman.gigi.model.ConnectionState
import com.skydoves.cloudy.Cloudy
import java.text.SimpleDateFormat
import java.util.*

/**
 * Connection info card with glassmorphism
 */
@Composable
fun ConnectionInfoCard(
    connection: Connection,
    connectionDuration: String,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = false
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Pulsing animation for connected status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Cloudy(radius = 25) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connected",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF1A237E)
                    )

                    // Status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFF4CAF50).copy(alpha = pulseAlpha))
                        )
                        Text(
                            text = "Active",
                            fontSize = 14.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF546E7A)
                        )
                    }
                }

                Divider(color = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF1A237E).copy(alpha = 0.15f))
                
                // Partner info
                InfoRow(
                    icon = Icons.Default.Person,
                    label = "Partner",
                    value = connection.partnerName
                )
                
                // Connection code
                InfoRow(
                    icon = Icons.Default.Key,
                    label = "Code",
                    value = connection.connectionCode
                )
                
                // Duration
                InfoRow(
                    icon = Icons.Default.Schedule,
                    label = "Connected for",
                    value = connectionDuration
                )
                
                // Disconnect button
                Button(
                    onClick = { showDisconnectDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Disconnect",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Disconnect",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
    
    // Disconnect confirmation dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect?") },
            text = { 
                Text("This will end your connection with ${connection.partnerName} and delete all scribbles. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        onDisconnect()
                    }
                ) {
                    Text("Disconnect", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    val isDarkRow = false
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isDarkRow) Color.White.copy(alpha = 0.7f) else Color(0xFF546E7A),
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (isDarkRow) Color.White.copy(alpha = 0.6f) else Color(0xFF546E7A)
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDarkRow) Color.White else Color(0xFF1A237E)
            )
        }
    }
}
