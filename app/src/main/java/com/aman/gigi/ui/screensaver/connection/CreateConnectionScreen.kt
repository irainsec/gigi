package com.aman.gigi.ui.screensaver.connection

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.utils.ConnectionCodeGenerator
import com.aman.gigi.utils.QRCodeGenerator
import com.skydoves.cloudy.Cloudy

/**
 * Screen for creating a new connection (shows QR code and connection code)
 *
 * NOTE: Group connection gating — this screen handles only 1-to-1 connections.
 * The `isGroup` flag is passed into NamingScreen from the call-site (navigation /
 * ViewModel layer) before reaching here. To gate group creation behind the plan,
 * check `AppConfig.userPlan.features.groupConnections` at the point where the
 * caller decides to set `isGroup = true` and show UpgradeSheet if the feature is
 * not available. No group toggle lives in CreateConnectionScreen or NamingScreen
 * themselves.
 */
@Composable
fun CreateConnectionScreen(
    connectionCode: String = ConnectionCodeGenerator.generateCode(),
    partnerName: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = false
    val clipboardManager = LocalClipboardManager.current
    var showCopiedMessage by remember { mutableStateOf(false) }

    // Generate QR code
    val qrCodeBitmap = remember(connectionCode) {
        QRCodeGenerator.generateQRCode(connectionCode, 400)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        // Glass card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(32.dp))
                .border(1.5.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
        ) {
            Cloudy(radius = 35) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.15f))
                )
            }

            Surface(
                color = if (isDark) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color(0xFFE6E0FF).copy(alpha = 0.85f),
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connecting with $partnerName",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B5CF6)
                    )

                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // QR Code
                Image(
                    bitmap = qrCodeBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Connection Code
                Text(
                    text = "Or share this code:",
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFFB0A8D0) else Color.Black.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val formattedCode = if (connectionCode.length == 8) {
                        "${connectionCode.substring(0, 4)}-${connectionCode.substring(4, 8)}"
                    } else {
                        connectionCode
                    }
                    Text(
                        text = formattedCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8B5CF6),
                        letterSpacing = 4.sp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(connectionCode))
                            showCopiedMessage = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = Color(0xFF8B5CF6)
                        )
                    }
                }

                if (showCopiedMessage) {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2000)
                        showCopiedMessage = false
                    }

                    Text(
                        text = "Copied!",
                        fontSize = 12.sp,
                        color = Color(0xFF03DAC6),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Instructions
                Text(
                    text = "Waiting for partner to scan or enter code...",
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFFB0A8D0) else Color.Black.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
}
