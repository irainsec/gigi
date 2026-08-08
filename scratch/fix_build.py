import os

# Fix Music.kt
music_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(music_path, 'r', encoding='utf-8') as f:
    music_content = f.read()

# Replace PlayerThemePreset.AUTO -> with MOOD_OF_DAY branch as well
lines = music_content.splitlines()
out_lines = []
for line in lines:
    out_lines.append(line)
    if "PlayerThemePreset.AUTO ->" in line:
        # Check if it has a return value on the same line
        if "listOf(" in line or "Color(" in line or "getPaletteForTheme" in line or "autoPalette" in line:
            # We need to duplicate the line and replace AUTO with MOOD_OF_DAY
            new_line = line.replace("PlayerThemePreset.AUTO", "PlayerThemePreset.MOOD_OF_DAY")
            if "autoPalette" in line:
                new_line = new_line.replace("autoPalette", "getPaletteForTheme(PlayerThemePreset.SAKURA_SPRING)")
            out_lines.append(new_line)
        else:
            out_lines.append(line.replace("PlayerThemePreset.AUTO", "PlayerThemePreset.MOOD_OF_DAY"))

music_content = "\n".join(out_lines) + "\n"

with open(music_path, 'w', encoding='utf-8') as f:
    f.write(music_content)

# Fix LoveCardsSection.kt
love_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/LoveCardsSection.kt"
with open(love_path, 'r', encoding='utf-8') as f:
    love_content = f.read()

# Replace Icons.Default.Lock and Icons.Default.LockOpen that had the fully qualified name issue.
# In LoveCardsSection.kt there's: 
# `imageVector = if (isTimeCapsule) androidx.compose.material.icons.Icons.Default.Lock else androidx.compose.material.icons.Icons.Default.LockOpen`
# and `imageVector = androidx.compose.material.icons.Icons.Default.Lock`
love_content = love_content.replace("androidx.compose.material.icons.Icons.Default.LockOpen", "Icons.Default.LockOpen")
love_content = love_content.replace("androidx.compose.material.icons.Icons.Default.Lock", "Icons.Default.Lock")

# Add imports to LoveCardsSection.kt
imports = """
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.draw.blur
"""
if "import androidx.compose.material.icons.filled.Lock" not in love_content:
    love_content = love_content.replace("package com.aman.gigi.ui\n", f"package com.aman.gigi.ui\n{imports}\n")

with open(love_path, 'w', encoding='utf-8') as f:
    f.write(love_content)

print("Done fixing build errors")
