import re

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add emoji state
old_state = """    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf<String?>(null) }"""

new_state = """    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf<String?>(null) }
    var emoji by remember { mutableStateOf("??") }"""
content = content.replace(old_state, new_state)

# Add emoji init
old_init = """        if (reminder != null) {
            title = reminder.title
            description = reminder.description"""

new_init = """        if (reminder != null) {
            title = reminder.title
            description = reminder.description
            emoji = reminder.emoji ?: "??" """
content = content.replace(old_init, new_init)

old_else = """        } else {
            title = ""
            description = null"""

new_else = """        } else {
            title = ""
            description = null
            emoji = "??" """
content = content.replace(old_else, new_else)


# Update the button call
old_button = """            Button(
                onClick = {
                    onSave(
                        title,
                        description,
                        dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        recurrencePattern,
                        if (recurrencePattern == RecurrencePattern.CUSTOM) 
                            customIntervalQuantity.toLongOrNull()?.let { it * customIntervalUnit.duration.toMillis() } 
                        else null,
                        if (limitTimeFrame) startHour else null,
                        if (limitTimeFrame) startMinute else null,
                        if (limitTimeFrame) endHour else null,
                        if (limitTimeFrame) endMinute else null
                    )
                },"""

new_button = """            Button(
                onClick = {
                    onSave(
                        title,
                        description,
                        dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        recurrencePattern,
                        if (recurrencePattern == RecurrencePattern.CUSTOM) 
                            customIntervalQuantity.toLongOrNull()?.let { it * customIntervalUnit.duration.toMillis() } 
                        else null,
                        if (limitTimeFrame) startHour else null,
                        if (limitTimeFrame) startMinute else null,
                        if (limitTimeFrame) endHour else null,
                        if (limitTimeFrame) endMinute else null,
                        emoji
                    )
                },"""
content = content.replace(old_button, new_button)

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'w', encoding='utf-8') as f:
    f.write(content)
