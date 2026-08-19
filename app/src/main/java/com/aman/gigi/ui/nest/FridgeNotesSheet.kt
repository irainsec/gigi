package com.aman.gigi.ui.nest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.data.nest.FridgeNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeNotesSheet(
    notes: List<FridgeNote>,
    onDismiss: () -> Unit,
    onAddNote: (text: String, color: String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var isAddingNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FEF08A") }

    val colorOptions = listOf(
        "#FEF08A" to Color(0xFFFEF08A), // Banana Yellow
        "#FBCFE8" to Color(0xFFFBCFE8), // Pastel Pink
        "#A7F3D0" to Color(0xFFA7F3D0), // Mint Green
        "#DDD6FE" to Color(0xFFDDD6FE), // Lavender
        "#BAE6FD" to Color(0xFFBAE6FD)  // Sky Blue
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16102A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🧊", fontSize = 24.sp)
                    Text(
                        text = "Fridge Magnet Notes",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                IconButton(
                    onClick = { isAddingNote = !isAddingNote },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF8B5CF6)
                    )
                ) {
                    Icon(
                        imageVector = if (isAddingNote) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Add Note",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Add Note Form
            if (isAddingNote) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF231842),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Stick a note for your partner 💌",
                            color = Color(0xFFDDD6FE),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = { Text("Leave a sweet note or reminder...", color = Color(0xFF9CA3AF)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFC084FC),
                                unfocusedBorderColor = Color(0xFF4C3875)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Color picker
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colorOptions.forEach { (hex, col) ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (selectedColor == hex) 3.dp else 1.dp,
                                            color = if (selectedColor == hex) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = hex }
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    if (noteText.isNotBlank()) {
                                        onAddNote(noteText.trim(), selectedColor)
                                        noteText = ""
                                        isAddingNote = false
                                    }
                                },
                                enabled = noteText.isNotBlank(),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                            ) {
                                Text("Stick It ✨", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Notes Grid
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧲", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No notes on the fridge yet.\nTap '+' to leave the first sweet note!",
                            color = Color(0xFFA78BFA),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        val bg = runCatching { Color(android.graphics.Color.parseColor(note.color)) }
                            .getOrDefault(Color(0xFFFEF08A))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = bg,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🧲", fontSize = 14.sp)
                                        IconButton(
                                            onClick = { onDeleteNote(note.id) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.Black.copy(alpha = 0.45f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = note.text,
                                        color = Color(0xFF1E1B4B),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 18.sp
                                    )
                                }

                                Text(
                                    text = "— ${note.authorName}",
                                    color = Color.Black.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
