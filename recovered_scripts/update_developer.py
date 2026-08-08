import re

with open('app/src/main/java/com/aman/gigi/ui/Developer.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update ConnectionActionRow signature
old_sig = """private fun ConnectionActionRow(
    connection: Connection,
    onDoodle: () -> Unit,
    onSparkle: () -> Unit,
    onChat: () -> Unit
)"""
new_sig = """private fun ConnectionActionRow(
    connection: Connection,
    onDoodle: () -> Unit,
    onSparkle: () -> Unit,
    onChat: () -> Unit,
    onBreak: () -> Unit
)"""
content = content.replace(old_sig, new_sig)

# 2. Update ConnectionActionRow call
old_call = """                    ConnectionActionRow(
                        connection = currentPartner,
                        onDoodle = { viewModel.setDrawingMode(true) },
                        onSparkle = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.SPARKLE, currentPartner.connectionId) },
                        onChat = { viewModel.openChat(currentPartner.connectionId) }
                    )"""
new_call = """                    ConnectionActionRow(
                        connection = currentPartner,
                        onDoodle = { viewModel.setDrawingMode(true) },
                        onSparkle = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.SPARKLE, currentPartner.connectionId) },
                        onChat = { viewModel.openChat(currentPartner.connectionId) },
                        onBreak = { viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.BREAK_CARDS, currentPartner.connectionId) }
                    )"""
content = content.replace(old_call, new_call)

# 3. Update ConnectionActionRow layout
old_layout = """    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ActionTile(
            emoji = "💬",
            title = "Chat",
            subtitle = if (isGroup) "Group chat" else "Message",
            accent = Color(0xFF8B5CF6),
            modifier = Modifier.weight(1f),
            onClick = onChat
        )
        ActionTile(
            emoji = "🎨",
            title = "Doodle",
            subtitle = if (isGroup) "To everyone" else "Draw together",
            accent = Color(0xFF7C3AED),
            modifier = Modifier.weight(1f),
            onClick = onDoodle
        )
        ActionTile(
            emoji = "📸",
            title = "Sparkle",
            subtitle = if (isGroup) "Send to group" else "Share a photo",
            accent = Color(0xFFEC4899),
            modifier = Modifier.weight(1f),
            onClick = onSparkle
        )
    }"""
new_layout = """    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTile(
                emoji = "💬",
                title = "Chat",
                subtitle = if (isGroup) "Group chat" else "Message",
                accent = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f),
                onClick = onChat
            )
            ActionTile(
                emoji = "🎨",
                title = "Doodle",
                subtitle = if (isGroup) "To everyone" else "Draw together",
                accent = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f),
                onClick = onDoodle
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTile(
                emoji = "📸",
                title = "Sparkle",
                subtitle = if (isGroup) "Send to group" else "Share a photo",
                accent = Color(0xFFEC4899),
                modifier = Modifier.weight(1f),
                onClick = onSparkle
            )
            ActionTile(
                emoji = "☕",
                title = "Break",
                subtitle = if (isGroup) "Group break" else "Take a break",
                accent = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
                onClick = onBreak
            )
        }
    }"""
content = content.replace(old_layout, new_layout)

with open('app/src/main/java/com/aman/gigi/ui/Developer.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated Developer.kt")
