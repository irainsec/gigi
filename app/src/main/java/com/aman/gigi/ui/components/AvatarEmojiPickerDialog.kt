package com.aman.gigi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest

@Composable
fun AvatarEmojiPickerDialog(
    onDismiss: () -> Unit,
    onPickEmoji: (String) -> Unit,
    onOpenTwigiStudio: (() -> Unit)? = null,
    title: String = "Pick an emoji",
    subtitle: String = ""
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .crossfade(true)
            // defaults are fine
            .build()
    }

    val prefs = context.getSharedPreferences("emoji_prefs", android.content.Context.MODE_PRIVATE)
    var recentEmojis by remember { 
        mutableStateOf(prefs.getString("recent_emojis", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList())
    }

    val categoriesMap = remember {
        val map = mutableMapOf<String, MutableList<String>>()
        TELEGRAM_EMOJIS.forEach { url ->
            val parts = url.split("/")
            if (parts.size >= 2) {
                val cat = parts[parts.size - 2].replace("%20", " ")
                map.getOrPut(cat) { mutableListOf() }.add(url)
            }
        }
        map
    }
    
    val allCategories = remember { listOf("Recent") + categoriesMap.keys.toList() }
    var selectedCategory by remember { 
        mutableStateOf(if (recentEmojis.isNotEmpty()) "Recent" else allCategories.getOrElse(1) { "Recent" }) 
    }

    val displayedEmojis = if (selectedCategory == "Recent") {
        recentEmojis
    } else {
        categoriesMap[selectedCategory] ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                if (onOpenTwigiStudio != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onOpenTwigiStudio,
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF7C3AED)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("✨ Switch to 3D Twigi Studio 🎭", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ScrollableTabRow(
                    selectedTabIndex = allCategories.indexOf(selectedCategory).coerceAtLeast(0),
                    edgePadding = 24.dp,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    divider = {}
                ) {
                    allCategories.forEach { cat ->
                        Tab(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            text = { Text(cat) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (displayedEmojis.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No recent emojis yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)
                    ) {
                        items(displayedEmojis) { url ->
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = "Emoji",
                                contentScale = ContentScale.Fit,
                                loading = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val updated = (listOf(url) + recentEmojis).distinct().take(12)
                                        prefs.edit().putString("recent_emojis", updated.joinToString(",")).apply()
                                        recentEmojis = updated
                                        onPickEmoji(url)
                                        onDismiss()
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 24.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
