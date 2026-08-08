import re

# Fix ScribblePlaybackComponent.kt (ContentScale)
file1 = 'app/src/main/java/com/aman/gigi/ui/screensaver/components/ScribblePlaybackComponent.kt'
with open(file1, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('contentScale = ContentScale.Crop,', 'contentScale = ContentScale.Fit,')

with open(file1, 'w', encoding='utf-8') as f:
    f.write(content)

# Fix Screensaver.kt (canSendInteractiveActions & Poke overlay)
file2 = 'app/src/main/java/com/aman/gigi/ui/Screensaver.kt'
with open(file2, 'r', encoding='utf-8') as f:
    content2 = f.read()

# Change canSendInteractiveActions to just be true for now, or check presence
content2 = content2.replace(
    'val canSendInteractiveActions = serverStatus.mode == com.aman.gigi.model.ServerMode.ONLINE',
    'val canSendInteractiveActions = true // Allow taps, let ViewModel/SyncManager handle offline states'
)

with open(file2, 'w', encoding='utf-8') as f:
    f.write(content2)

# Fix ScribbleSyncManager to show Toast on poke
file3 = 'app/src/main/java/com/aman/gigi/data/sync/ScribbleSyncManager.kt'
with open(file3, 'r', encoding='utf-8') as f:
    content3 = f.read()

poke_replacement = """            com.aman.gigi.utils.Constants.COMMAND_POKE -> {
                val emoji = json.optJSONObject("data")?.optString("emoji", "??") ?: "??"
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    android.widget.Toast.makeText(context, "Poke received! ", android.widget.Toast.LENGTH_SHORT).show()
                }
                scope.launch {
                    _events.emit(SyncEvent.Poke(connectionId, emoji))
                }
            }"""

content3 = re.sub(
    r'com\.aman\.gigi\.utils\.Constants\.COMMAND_POKE -> \{.*?_events\.emit\(SyncEvent\.Poke\(connectionId, emoji\)\)\s*\}\s*\}',
    poke_replacement,
    content3,
    flags=re.DOTALL
)

with open(file3, 'w', encoding='utf-8') as f:
    f.write(content3)
