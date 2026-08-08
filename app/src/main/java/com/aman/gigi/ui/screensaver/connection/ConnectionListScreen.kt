package com.aman.gigi.ui.screensaver.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import coil.compose.AsyncImage
import com.aman.gigi.model.Connection
import com.aman.gigi.model.ServerMode
import com.aman.gigi.model.ServerStatus
import com.aman.gigi.ui.screensaver.components.SafeGlassBox

// Colors for the new design
private val Lavender = Color(0xFF8B5CF6)
private val Ink = Color(0xFF3B2A6B)
private val Muted = Color(0xFF9A8FC0)

@Composable
fun ConnectionListScreen(
    connections: List<Connection>,
    canAddConnection: Boolean,
    serverStatus: ServerStatus,
    onAddConnection: () -> Unit,
    onAddGroup: () -> Unit,
    onJoinWithCode: () -> Unit,
    onScribbleToPartner: (Connection) -> Unit,
    onDisconnect: (Connection) -> Unit,
    onManageGroup: ((Connection) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    var showUpgradeSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (onDismiss != null) Color(0xFF0F0B1A).copy(alpha = 0.65f) else Color.Transparent)
            .clickable(enabled = onDismiss != null) { onDismiss?.invoke() }
    ) {
        if (onDismiss == null) {
            com.aman.gigi.ui.components.RomanceAmbientDecor(
                modifier = Modifier.fillMaxSize(),
                darkTheme = false
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            if (serverStatus.mode != ServerMode.ONLINE) {
                ServerBanner(serverStatus = serverStatus)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Your connections 💫",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap to open, or start something new.",
                                fontSize = 12.sp,
                                color = Muted
                            )
                        }
                        if (onDismiss != null) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Muted
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    if (connections.isEmpty()) {
                        EmptyConnectionsState(canAddConnection = canAddConnection)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(connections, key = { it.connectionId }) { connection ->
                                ConnectionRow(
                                    connection = connection,
                                    onClick = { onScribbleToPartner(connection) }
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Bottom Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            title = "New Connection",
                            icon = "💞",
                            onClick = {
                                if (!canAddConnection) showUpgradeSheet = true
                                else onAddConnection()
                            }
                        )
                        ActionCard(
                            title = "New Group",
                            icon = "👯‍♀️",
                            onClick = {
                                if (!canAddConnection) showUpgradeSheet = true
                                else onAddGroup()
                            }
                        )
                        ActionCard(
                            title = "Join with a code",
                            icon = "🔗",
                            onClick = onJoinWithCode
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))
        }

        if (showUpgradeSheet) {
            com.aman.gigi.ui.components.UpgradeSheet(
                featureName = "More Connections",
                featureDescription = "Add more partners and groups to your Gigi.",
                onDismiss = { showUpgradeSheet = false }
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF3EDFF),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF532490)
            )
        }
    }
}

@Composable
private fun ConnectionRow(
    connection: Connection,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF6F3FB))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFEBE3FB)),
            contentAlignment = Alignment.Center
        ) {
            val ge = connection.partnerEmojiUrl
            if (!ge.isNullOrBlank()) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val loader = remember {
                    coil.ImageLoader.Builder(ctx).components {
                        if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                        else add(coil.decode.GifDecoder.Factory())
                    }.build()
                }
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(ctx).data(ge).build(),
                    imageLoader = loader,
                    contentDescription = connection.partnerName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            } else if (!connection.partnerAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = connection.partnerAvatarUrl,
                    contentDescription = connection.partnerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(connection.partnerEmoji.ifBlank { "🌻" }, fontSize = 24.sp)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = connection.partnerName.ifEmpty { "Waiting..." },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Ink
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (connection.isGroup) "Group" else "Connection",
                fontSize = 12.sp,
                color = Muted
            )
        }
        
        Text(">", fontSize = 20.sp, color = Muted)
    }
}

@Composable
private fun ServerBanner(serverStatus: ServerStatus) {
    val title = if (serverStatus.mode == ServerMode.MAINTENANCE) "Server maintenance" else "Server offline"
    val message = serverStatus.message?.ifBlank { "Trying to reach the sync server." } ?: "Trying to reach the sync server."
    SafeGlassBox(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun EmptyConnectionsState(canAddConnection: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No connections yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Start something new below!",
            fontSize = 14.sp,
            color = Muted,
            textAlign = TextAlign.Center
        )
    }
}
