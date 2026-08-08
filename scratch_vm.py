import re

with open('app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Update ReceivedQuoteOverlay class
content = content.replace(
    'data class ReceivedQuoteOverlay(\n    val connectionId: String,\n    val senderName: String,\n    val quote: String\n)',
    'data class ReceivedQuoteOverlay(\n    val connectionId: String,\n    val senderName: String,\n    val quote: String,\n    val emoji: String = "??"\n)'
)

# Update showQuoteOverlay definition
content = content.replace(
    'private fun showQuoteOverlay(\n        connectionId: String,\n        quote: String,\n        senderName: String\n    ) {',
    'private fun showQuoteOverlay(\n        connectionId: String,\n        quote: String,\n        senderName: String,\n        emoji: String = "??"\n    ) {'
)

# Update _quoteOverlay.value inside showQuoteOverlay
old_assign = """        _quoteOverlay.value = ReceivedQuoteOverlay(
            connectionId = connectionId,
            senderName = senderName.ifBlank { selectedConnection.value?.partnerName ?: "Your partner" },
            quote = quote
        )"""

new_assign = """        _quoteOverlay.value = ReceivedQuoteOverlay(
            connectionId = connectionId,
            senderName = senderName.ifBlank { selectedConnection.value?.partnerName ?: "Your partner" },
            quote = quote,
            emoji = emoji
        )"""
content = content.replace(old_assign, new_assign)

# Update the call in AlarmDoneTogether
old_call = """                    is SyncEvent.AlarmDoneTogether -> {
                        showQuoteOverlay(
                            connectionId = event.connectionId,
                            quote = ": ",
                            senderName = "Partner" // Will be replaced by UI using connectionId
                        )
                    }"""

new_call = """                    is SyncEvent.AlarmDoneTogether -> {
                        showQuoteOverlay(
                            connectionId = event.connectionId,
                            quote = ": ",
                            senderName = "Partner",
                            emoji = event.emoji
                        )
                    }"""
content = content.replace(old_call, new_call)

with open('app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)
