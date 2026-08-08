package com.aman.gigi.ui.screensaver.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.ui.screensaver.connection.GlassButton
import com.aman.gigi.utils.ConnectionCodeGenerator
import com.skydoves.cloudy.Cloudy

/**
 * Screen for joining an existing connection (enter code or scan QR)
 */
@Composable
fun JoinConnectionScreen(
    onJoinWithCode: (code: String, partnerName: String, relationshipType: String) -> Unit,
    onScanQR: () -> Unit,
    onCancel: () -> Unit,
    initialCode: String = "",
    modifier: Modifier = Modifier
) {
    val isDark = false
    var connectionCode by remember(initialCode) { mutableStateOf(initialCode) }
    var partnerName by remember { mutableStateOf("") }
    var relationshipType by remember { mutableStateOf("ROMANTIC") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                color = if (isDark) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color(0xFF1F1838).copy(alpha = 0.96f),
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Join Your Person 💜",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scan QR Button
                OutlinedButton(
                    onClick = onScanQR,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFA78BFA)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan QR Code",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.2f))
                    Text(
                        text = "  OR ENTER DETAILS  ",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.2f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Connection Code Input Field
                Text(
                    text = "Connection Code",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC4B5FD),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = connectionCode,
                    onValueChange = {
                        connectionCode = it.uppercase()
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "KD4Z-JAYX",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    ),
                    singleLine = true,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFA78BFA),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        errorBorderColor = Color.Red,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Partner Name / Alias Field
                Text(
                    text = "Their Name / Alias 🌸",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC4B5FD),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = partnerName,
                    onValueChange = { partnerName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "e.g. Shreya / My Person",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFA78BFA),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Connection Theme Selection
                Text(
                    text = "Connection Theme 🎨",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC4B5FD),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                val themeOptions = listOf(
                    Triple("ROMANTIC", "Rose 🌹", Color(0xFFF472B6)),
                    Triple("BESTIE", "Golden 🌟", Color(0xFFFBBF24)),
                    Triple("FRIENDSHIP", "Ocean 🌊", Color(0xFF60A5FA)),
                    Triple("FAMILY", "Mint 🌿", Color(0xFF34D399))
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    themeOptions.forEach { (typeKey, label, accentColor) ->
                        val isSel = relationshipType == typeKey
                        Surface(
                            onClick = { relationshipType = typeKey },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) accentColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSel) 1.5.dp else 1.dp,
                                if (isSel) accentColor else Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Join Button
                Button(
                    onClick = {
                        val normalized = ConnectionCodeGenerator.normalizeCode(connectionCode)
                        if (ConnectionCodeGenerator.isValidCode(normalized)) {
                            onJoinWithCode(normalized, partnerName.ifBlank { "My Person" }, relationshipType)
                        } else {
                            errorMessage = "Invalid connection code format"
                        }
                    },
                    enabled = connectionCode.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(
                        text = "Join & Add to Galaxy ✨",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
}
