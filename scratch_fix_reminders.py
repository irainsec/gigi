import re

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix imports
if "import androidx.compose.foundation.shape.CircleShape" not in content:
    content = content.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.shape.CircleShape")
if "import androidx.compose.ui.unit.sp" not in content:
    content = content.replace("import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp")

# Fix line 552: onSave signature
old_onsave = "onSave = { title, description, dueDate, recurrencePattern, customIntervalMillis, startH, startM, endH, endM ->"
new_onsave = "onSave = { title, description, dueDate, recurrencePattern, customIntervalMillis, startH, startM, endH, endM, emoji ->"
content = content.replace(old_onsave, new_onsave)

# Fix saveSharedAlarm call to include emoji
old_save = """                                        viewModel.saveSharedAlarm(
                                            existingAlarm = editingSharedAlarm,
                                            connectionId = connection.connectionId,
                                            title = title,
                                            description = description,
                                            dueDate = dueDate,
                                            recurrencePattern = recurrencePattern,
                                            customIntervalMillis = customIntervalMillis,
                                            startH = startH,
                                            startM = startM,
                                            endH = endH,
                                            endM = endM
                                        )"""
new_save = """                                        viewModel.saveSharedAlarm(
                                            existingAlarm = editingSharedAlarm,
                                            connectionId = connection.connectionId,
                                            title = title,
                                            description = description,
                                            dueDate = dueDate,
                                            recurrencePattern = recurrencePattern,
                                            customIntervalMillis = customIntervalMillis,
                                            startH = startH,
                                            startM = startM,
                                            endH = endH,
                                            endM = endM,
                                            emoji = emoji
                                        )"""
content = content.replace(old_save, new_save)

# Fix line 1422: onSave call
old_call = """                    onSave(
                        title,
                        description,
                        dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        recurrencePattern,
                        customMillis,
                        if (limitTimeFrame) startHour else null,
                        if (limitTimeFrame) startMinute else null,
                        if (limitTimeFrame) endHour else null,
                        if (limitTimeFrame) endMinute else null
                    )"""
new_call = """                    onSave(
                        title,
                        description,
                        dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        recurrencePattern,
                        customMillis,
                        if (limitTimeFrame) startHour else null,
                        if (limitTimeFrame) startMinute else null,
                        if (limitTimeFrame) endHour else null,
                        if (limitTimeFrame) endMinute else null,
                        emoji
                    )"""
content = content.replace(old_call, new_call)

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'w', encoding='utf-8') as f:
    f.write(content)


# Fix LatestNoteWidget.kt
with open('app/src/main/java/com/aman/gigi/widget/LatestNoteWidget.kt', 'r', encoding='utf-8') as f:
    wn_content = f.read()

if "import androidx.compose.ui.graphics.Color" not in wn_content:
    wn_content = wn_content.replace("import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.graphics.Color")

wn_content = wn_content.replace("ColorProvider(0xFFFFE0E0)", "ColorProvider(Color(0xFFFFE0E0))")
wn_content = wn_content.replace("ColorProvider(0xFFD32F2F)", "ColorProvider(Color(0xFFD32F2F))")
wn_content = wn_content.replace("ColorProvider(0xFF455A64)", "ColorProvider(Color(0xFF455A64))")

with open('app/src/main/java/com/aman/gigi/widget/LatestNoteWidget.kt', 'w', encoding='utf-8') as f:
    f.write(wn_content)


# Fix SharedCountdownWidget.kt
with open('app/src/main/java/com/aman/gigi/widget/SharedCountdownWidget.kt', 'r', encoding='utf-8') as f:
    wc_content = f.read()

if "import androidx.compose.ui.graphics.Color" not in wc_content:
    wc_content = wc_content.replace("import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.graphics.Color")

wc_content = wc_content.replace("ColorProvider(0xFFE6E0FF)", "ColorProvider(Color(0xFFE6E0FF))")
wc_content = wc_content.replace("ColorProvider(0xFF6200EE)", "ColorProvider(Color(0xFF6200EE))")
wc_content = wc_content.replace("ColorProvider(0xFF455A64)", "ColorProvider(Color(0xFF455A64))")

with open('app/src/main/java/com/aman/gigi/widget/SharedCountdownWidget.kt', 'w', encoding='utf-8') as f:
    f.write(wc_content)

