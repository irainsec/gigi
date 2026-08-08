import re

# Update Reminder.kt
with open('app/src/main/java/com/aman/gigi/model/Reminder.kt', 'r', encoding='utf-8') as f:
    rem = f.read()
rem = rem.replace('val repeatEndMinute: Int? = null\n)', 'val repeatEndMinute: Int? = null,\n    val emoji: String? = null\n)')
with open('app/src/main/java/com/aman/gigi/model/Reminder.kt', 'w', encoding='utf-8') as f:
    f.write(rem)

# Update SharedAlarmMirror.kt
with open('app/src/main/java/com/aman/gigi/model/SharedAlarmMirror.kt', 'r', encoding='utf-8') as f:
    sam = f.read()

sam = sam.replace('val repeatEndMinute: Int? = null,', 'val repeatEndMinute: Int? = null,\n    val emoji: String? = null,')
sam = sam.replace('repeatEndMinute = repeatEndMinute\n        )', 'repeatEndMinute = repeatEndMinute,\n            emoji = emoji\n        )')

with open('app/src/main/java/com/aman/gigi/model/SharedAlarmMirror.kt', 'w', encoding='utf-8') as f:
    f.write(sam)
