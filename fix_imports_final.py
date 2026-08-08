
import os

filepath = r'c:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\ui\LoveCardsSection.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
added = False
for line in lines:
    if 'import androidx.compose.foundation.gestures.detectVerticalDragGestures' in line:
        new_lines.append(line)
        if not added:
            new_lines.append('import androidx.compose.ui.input.pointer.pointerInput\n')
            new_lines.append('import androidx.compose.foundation.gestures.detectTapGestures\n')
            added = True
    elif 'import androidx.compose.foundation.hapticfeedback.HapticFeedbackType' in line:
        continue # Remove incorrect import
    else:
        new_lines.append(line)

with open(filepath, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print("Imports fixed")
