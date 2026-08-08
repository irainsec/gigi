import os

file_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix Icons - we just need to replace androidx.compose.material.icons.Icons.Default.X 
# with Icons.Default.X and ADD imports.
content = content.replace("androidx.compose.material.icons.Icons.Default.Timer", "Icons.Default.Timer")
content = content.replace("androidx.compose.material.icons.Icons.Default.Subject", "Icons.Default.Subject")
content = content.replace("androidx.compose.material.icons.Icons.Default.Favorite", "Icons.Default.Favorite")
content = content.replace("androidx.compose.material.icons.Icons.Default.FavoriteBorder", "Icons.Default.FavoriteBorder")
content = content.replace("androidx.compose.material.icons.Icons.Default.QueueMusic", "Icons.Default.QueueMusic")
content = content.replace("androidx.compose.material.icons.Icons.Default.Shuffle", "Icons.Default.Shuffle")
content = content.replace("androidx.compose.material.icons.Icons.Default.RepeatOne", "Icons.Default.RepeatOne")
content = content.replace("androidx.compose.material.icons.Icons.Default.Repeat", "Icons.Default.Repeat")

imports = """
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Repeat
"""

if "import androidx.compose.material.icons.filled.Timer" not in content:
    content = content.replace("package com.aman.gigi.ui\n", f"package com.aman.gigi.ui\n{imports}\n")

# Fix lyricline startTimeMs -> timestampMs
content = content.replace("it.startTimeMs", "it.timestampMs")

# Fix missing MOOD_OF_DAY branch.
content = content.replace("ThemePreset.AUTO -> autoPalette", "ThemePreset.AUTO -> autoPalette\n        ThemePreset.MOOD_OF_DAY -> getPaletteForTheme(ThemePreset.SAKURA_SPRING)")

lines = content.splitlines()
out_lines = []
for line in lines:
    if line.strip() == "ThemePreset.AUTO ->":
        out_lines.append(line)
        out_lines.append(line.replace("AUTO", "MOOD_OF_DAY"))
    else:
        out_lines.append(line)

content = "\n".join(out_lines) + "\n"

# Write back
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done fixing Music.kt")
