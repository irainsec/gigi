package com.aman.gigi.ui.screensaver.connection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aman.gigi.model.RemoteNotification
import com.aman.gigi.viewmodel.ScreensaverViewModel
import java.text.SimpleDateFormat
import java.util.*

private val PurpleAccent = Color(0xFF7C3AED)
private val DeepPurple = Color(0xFF4C1D95)
private val CardBg = Color(0xFF1E1040)
private val CardBgLight = Color(0xFF2A1A5E)
private val TextWhite = Color.White
private val TextMuted = Color.White.copy(alpha = 0.55f)
private val SearchBarBg = Color.White.copy(alpha = 0.08f)

/** Friendly display name from package name */
private fun friendlyAppName(packageName: String): String {
    val known = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.google.android.gm" to "Gmail",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.twitter.android" to "Twitter",
        "com.snapchat.android" to "Snapchat",
        "org.telegram.messenger" to "Telegram",
        "com.spotify.music" to "Spotify",
        "com.android.mms" to "Messages",
        "com.google.android.apps.messaging" to "Messages",
        "com.microsoft.teams" to "Teams",
        "com.slack" to "Slack"
    )
    return known[packageName]
        ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

@Composable
fun NotificationListView(
    viewModel: ScreensaverViewModel,
    onClose: () -> Unit
) {
    val partnerConnectionId by viewModel.partnerConnectionId.collectAsState()
    val notifications by viewModel.getNotificationsForPartner(partnerConnectionId).collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }

    val displayList = if (isSearchMode && searchQuery.isNotBlank()) {
        notifications.filter {
            (it.title?.contains(searchQuery, ignoreCase = true) == true) ||
            (it.text?.contains(searchQuery, ignoreCase = true) == true)
        }
    } else {
        notifications
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = 20.dp, start = 16.dp, end = 16.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0D0825), Color(0xFF1A0840))),
                    RoundedCornerShape(28.dp)
                )
                .clip(RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            // ─── Header ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = PurpleAccent,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Notification Center",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        fontSize = 20.sp
                    )
                )
                Spacer(Modifier.weight(1f))
                if (isSearchMode) {
                    IconButton(onClick = {
                        isSearchMode = false
                        searchQuery = ""
                    }) {
                        Icon(Icons.Default.Close, null, tint = TextMuted)
                    }
                } else {
                    IconButton(onClick = { isSearchMode = true }) {
                        Icon(Icons.Default.Search, null, tint = PurpleAccent)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = TextWhite.copy(alpha = 0.6f))
                }
            }

            // ─── Search Bar ───────────────────────────────────────────
            AnimatedVisibility(
                visible = isSearchMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .background(SearchBarBg, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = LocalTextStyle.current.copy(color = TextWhite, fontSize = 16.sp),
                        cursorBrush = SolidColor(PurpleAccent),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search messages, titles...", color = TextMuted, fontSize = 15.sp)
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ─── Notification List / Empty State ───────────────────────
            if (displayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = PurpleAccent.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (isSearchMode) "No results found" else "No notifications yet",
                            color = TextMuted,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayList, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onDelete = { viewModel.deleteNotification(notification.id) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun NotificationItem(
    notification: RemoteNotification,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(CardBg, CardBgLight)),
                RoundedCornerShape(18.dp)
            )
            .padding(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // App icon or fallback
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(PurpleAccent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!notification.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = notification.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                    )
                } else if (!notification.iconBase64.isNullOrBlank()) {
                    // Legacy Base64 support for real-time notifications if URL isn't available yet
                    val bitmap = remember(notification.iconBase64) {
                        try {
                            val bytes = android.util.Base64.decode(notification.iconBase64, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                    } else {
                        FallbackIcon(notification.title ?: "")
                    }
                } else {
                    FallbackIcon(notification.title ?: "")
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (notification.title ?: friendlyAppName(notification.packageName ?: "")).ifBlank { "Notification" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestamp ?: 0L)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = friendlyAppName(notification.packageName ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = PurpleAccent.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = notification.text ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite.copy(alpha = 0.85f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FallbackIcon(title: String) {
    Text(
        text = (title.firstOrNull()?.uppercaseChar() ?: "?").toString(),
        color = PurpleAccent,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp
    )
}
