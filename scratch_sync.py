import re

with open('app/src/main/java/com/aman/gigi/data/sync/ScribbleSyncManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Update SyncEvent.AlarmDoneTogether
content = content.replace(
    'data class AlarmDoneTogether(val connectionId: String, val alarmTitle: String, val text: String) : SyncEvent()',
    'data class AlarmDoneTogether(val connectionId: String, val alarmTitle: String, val text: String, val emoji: String) : SyncEvent()'
)

# Update the parsing of AlarmDoneTogether
parsing_old = """                    val alarmTitle = it.optString("alarmTitle", "Alarm")
                    val text = it.optString("text", "Done Together!")
                    scope.launch {
                        _events.emit(SyncEvent.AlarmDoneTogether(connectionId, alarmTitle, text))
                    }"""

parsing_new = """                    val alarmTitle = it.optString("alarmTitle", "Alarm")
                    val text = it.optString("text", "Done Together!")
                    val emoji = it.optString("emoji", "??")
                    scope.launch {
                        _events.emit(SyncEvent.AlarmDoneTogether(connectionId, alarmTitle, text, emoji))
                    }"""

content = content.replace(parsing_old, parsing_new)

with open('app/src/main/java/com/aman/gigi/data/sync/ScribbleSyncManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)
