import re

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the title field with a Row containing an emoji selector and the title field.
old_title_field = """        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            value = title,
            onValueChange = { title = it },
            label = { Text("What needs to be done?") },
            singleLine = true,
            shape = shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.5f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                disabledContainerColor = Color.White.copy(alpha = 0.2f),
            )
        )"""

new_title_field = """        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Emoji Selector
            var showEmojiMenu by remember { mutableStateOf(false) }
            val emojis = listOf("??", "?", "??", "??", "??", "??", "??", "??", "??", "??", "??")
            
            Box {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp).clickable { showEmojiMenu = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
                
                DropdownMenu(
                    expanded = showEmojiMenu,
                    onDismissRequest = { showEmojiMenu = false }
                ) {
                    emojis.chunked(4).forEach { rowEmojis ->
                        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                            rowEmojis.forEach { e ->
                                IconButton(onClick = { emoji = e; showEmojiMenu = false }) {
                                    Text(text = e, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                value = title,
                onValueChange = { title = it },
                label = { Text("What needs to be done?") },
                singleLine = true,
                shape = shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.5f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                    disabledContainerColor = Color.White.copy(alpha = 0.2f),
                )
            )
        }"""

content = content.replace(old_title_field, new_title_field)

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'w', encoding='utf-8') as f:
    f.write(content)
