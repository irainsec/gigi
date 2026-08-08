package com.aman.gigi.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.aman.gigi.ui.components.TELEGRAM_EMOJIS
import com.aman.gigi.ui.screensaver.components.GifPickerTray
import com.aman.gigi.viewmodel.ScreensaverViewModel

private val Ink = Color(0xFF3B2A6B)
private val Lavender = Color(0xFF8B5CF6)
private val Muted = Color(0xFF9A8FC0)

// A curated set of cute animated stickers (subset of the 631 Telegram emojis).
private val STICKER_PICKS: List<String> by lazy {
    val want = listOf("Smiling", "Heart", "Kiss", "Hug", "Star-Struck", "Sparkles", "Party",
        "Rose", "Sun", "Rainbow", "Cat", "Dog", "Panda", "Crown", "Gift", "Cake",
        "Face%20Blowing%20a%20Kiss", "Smiling%20Face%20with%20Hearts", "Hugging%20Face",
        "Grinning", "Winking", "Sleeping", "Thinking", "Clapping", "Folded%20Hands",
        "Sparkling%20Heart", "Two%20Hearts", "Growing%20Heart", "Beating%20Heart")
    val picks = TELEGRAM_EMOJIS.filter { url -> want.any { url.contains(it) } }
    (picks + TELEGRAM_EMOJIS).distinct().take(120)
}

@Composable
fun ChatScreen(
    connectionId: String,
    title: String,
    relationshipType: String,
    onClose: () -> Unit,
    viewModel: ScreensaverViewModel
) {
    val context = LocalContext.current
    val loader = remember {
        ImageLoader.Builder(context).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }
    val isGroup = relationshipType.equals("GROUP", ignoreCase = true)
    val messages by viewModel.chatMessages(connectionId).collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    var tray by remember { mutableStateOf<String?>(null) }   // null | "sticker" | "gif"
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Tell the sync layer this chat is on screen so incoming messages DON'T pop a chat
    // head / re-open the window while you're already reading it.
    DisposableEffect(connectionId) {
        ChatPresence.openConnectionId = connectionId
        com.aman.gigi.service.ChatHeadService.hide(context)
        onDispose {
            if (ChatPresence.openConnectionId == connectionId) ChatPresence.openConnectionId = null
        }
    }

    fun send() {
        val t = draft.trim()
        if (t.isNotEmpty()) { viewModel.sendChat(connectionId, t); draft = "" }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFF6F1FF), Color(0xFFFBEFF7))))
                .imePadding()   // lift the whole chat above the keyboard (edge-to-edge IME)
        ) {
            // header
            Surface(color = Color.White.copy(alpha = 0.85f), shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isGroup) "💬 $title" else "💜 $title", fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
                    Text("Close", color = Lavender, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onClose() })
                }
            }

            // messages
            if (messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🫧", fontSize = 44.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Say hi to ${if (isGroup) "the group" else title} 💌",
                            color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { m -> ChatBubble(m, isGroup, loader) }
                }
            }

            // sticker / gif tray
            AnimatedVisibility(visible = tray != null) {
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Box(Modifier.height(240.dp).fillMaxWidth()) {
                        when (tray) {
                            "sticker" -> LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                contentPadding = PaddingValues(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(STICKER_PICKS, key = { it }) { url ->
                                    AsyncImage(
                                        model = url, imageLoader = loader, contentDescription = null,
                                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.sendChat(connectionId, "", url); tray = null }
                                    )
                                }
                            }
                            "gif" -> GifPickerTray(
                                onGifSelected = { url -> viewModel.sendChat(connectionId, "", url); tray = null },
                                onLocalGifSelected = { uri -> viewModel.sendChat(connectionId, "", uri.toString()); tray = null },
                                onDismiss = { tray = null },
                                recentGifs = emptyList()
                            )
                        }
                    }
                }
            }

            // input bar
            Surface(color = Color.White, shadowElevation = 10.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TrayButton("😊", tray == "sticker") { tray = if (tray == "sticker") null else "sticker" }
                    TrayButton("🎁", tray == "gif") { tray = if (tray == "gif") null else "gif" }
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        placeholder = { Text("Type something sweet…", color = Muted, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lavender, unfocusedBorderColor = Color(0xFFE3DAF7)
                        )
                    )
                    Surface(
                        onClick = { send() }, shape = CircleShape,
                        color = if (draft.isBlank()) Muted.copy(alpha = 0.4f) else Lavender,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("➤", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrayButton(emoji: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (active) Lavender.copy(alpha = 0.2f) else Color(0xFFF3EEFF),
        modifier = Modifier.size(42.dp)
    ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) } }
}

@Composable
private fun ChatBubble(m: com.aman.gigi.model.ChatMessage, isGroup: Boolean, loader: ImageLoader) {
    val mine = m.isMine
    val isMedia = m.gifUrl.isNotBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (isGroup && !mine && m.senderName.isNotBlank()) {
                Text(m.senderName, fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))
            }
            if (isMedia) {
                AsyncImage(
                    model = m.gifUrl, imageLoader = loader, contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 180.dp).heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp
                    ),
                    color = if (mine) Lavender else Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.widthIn(max = 260.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(m.text, color = if (mine) Color.White else Ink, fontSize = 15.sp,
                            modifier = Modifier.weight(1f, fill = false))
                        if (mine) {
                            // Honest delivery state instead of pretending everything sent.
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when (m.status.uppercase()) {
                                    "DELIVERED", "READ" -> "✓✓"
                                    "SENT" -> "✓"
                                    else -> "⏳"          // queued / still offline
                                },
                                color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
