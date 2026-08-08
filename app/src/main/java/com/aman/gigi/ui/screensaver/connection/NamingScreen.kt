package com.aman.gigi.ui.screensaver.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.model.Connection
import com.skydoves.cloudy.Cloudy

/**
 * Screen for entering a partner name or group name before creating a connection,
 * and selecting participants if it's a group.
 */
@Composable
fun NamingScreen(
    onNameEntered: (String, String, String?, List<Connection>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isGroup: Boolean = false,
    connections: List<Connection> = emptyList()
) {
    var partnerName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("ROMANTIC") }
    var groupEmoji by remember { mutableStateOf<String?>(null) }
    var showGroupEmojiPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Multi-select for group members
    val selectedConnections = remember { mutableStateListOf<Connection>() }
    
    val filteredConnections = remember(searchQuery, connections) {
        if (searchQuery.isBlank()) connections
        else connections.filter { it.partnerName.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0B1A).copy(alpha = 0.8f))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1E1738))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isGroup) "New Group 👯‍♀️" else "Name this Partner",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isGroup) "Pick an emoji, who to add, and a name." else "Give your partner a name and choose your connection theme.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isGroup) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val gbCtx = androidx.compose.ui.platform.LocalContext.current
                        val gbLoader = remember {
                            coil.ImageLoader.Builder(gbCtx).components {
                                if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                                else add(coil.decode.GifDecoder.Factory())
                            }.build()
                        }
                        
                        // Emoji Picker Button
                        Surface(
                            onClick = { showGroupEmojiPicker = true },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val ge = groupEmoji
                                if (ge != null) {
                                    coil.compose.AsyncImage(
                                        model = coil.request.ImageRequest.Builder(gbCtx).data(ge).build(),
                                        imageLoader = gbLoader,
                                        contentDescription = "Group emoji",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                    )
                                } else {
                                    Text("👯‍♀️", fontSize = 28.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(14.dp))
                        
                        OutlinedTextField(
                            value = partnerName,
                            onValueChange = { partnerName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Group name", color = Color.White.copy(alpha = 0.4f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA855F7),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = partnerName,
                        onValueChange = { partnerName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Partner's Name (e.g. My Love 🌻)", color = Color.White.copy(alpha = 0.4f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Connection Theme 🎨",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC4B5FD),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                            val isSel = selectedType == typeKey
                            Surface(
                                onClick = { selectedType = typeKey },
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
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else Color.White.copy(alpha = 0.7f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                if (isGroup) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Add members (${selectedConnections.size})",
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search connections...", color = Color.White.copy(alpha = 0.4f)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredConnections.isEmpty()) {
                        Text(
                            text = if (searchQuery.isBlank()) "You don't have any connections yet to invite." else "No connections found.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredConnections, key = { it.connectionId }) { conn ->
                                val isSelected = selectedConnections.contains(conn)
                                val bgColor = if (isSelected) Color(0xFF6C39FF).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(bgColor)
                                        .clickable {
                                            if (isSelected) {
                                                selectedConnections.remove(conn)
                                            } else {
                                                selectedConnections.add(conn)
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val ge = conn.partnerEmojiUrl
                                        if (!ge.isNullOrBlank()) {
                                            val ctx = androidx.compose.ui.platform.LocalContext.current
                                            val loader = remember {
                                                coil.ImageLoader.Builder(ctx).components {
                                                    if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                                                    else add(coil.decode.GifDecoder.Factory())
                                                }.build()
                                            }
                                            coil.compose.AsyncImage(
                                                model = coil.request.ImageRequest.Builder(ctx).data(ge).build(),
                                                imageLoader = loader,
                                                contentDescription = conn.partnerName,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize().padding(8.dp)
                                            )
                                        } else {
                                            Text(
                                                text = conn.partnerEmoji.ifBlank { "🌻" },
                                                fontSize = 24.sp
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = conn.partnerName,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .border(
                                                2.dp,
                                                if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.4f),
                                                CircleShape
                                            )
                                            .background(if (isSelected) Color(0xFFA855F7) else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                if (isGroup) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                        }
                        
                        Button(
                            onClick = { 
                                if (partnerName.isNotBlank()) {
                                    onNameEntered(partnerName, selectedType, groupEmoji, selectedConnections.toList()) 
                                }
                            },
                            enabled = partnerName.isNotBlank(),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFA855F7),
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.1f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text("Create", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp))
                        }
                    }
                } else {
                    Button(
                        onClick = { 
                            if (partnerName.isNotBlank()) {
                                onNameEntered(partnerName, selectedType, groupEmoji, selectedConnections.toList()) 
                            }
                        },
                        enabled = partnerName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA855F7),
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.1f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        )
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
    
    if (showGroupEmojiPicker) {
        com.aman.gigi.ui.components.AvatarEmojiPickerDialog(
            onDismiss = { showGroupEmojiPicker = false },
            onPickEmoji = { groupEmoji = it; showGroupEmojiPicker = false },
            title = "Group emoji ✨",
            subtitle = "Pick an animated emoji for this group."
        )
    }
}
