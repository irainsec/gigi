import re

with open('app/src/main/java/com/aman/gigi/ui/Screensaver.kt', 'r', encoding='utf-8') as f:
    content = f.read()

import_line = "import androidx.compose.foundation.interaction.MutableInteractionSource"
if import_line not in content:
    content = content.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\n" + import_line)

with open('app/src/main/java/com/aman/gigi/ui/Screensaver.kt', 'w', encoding='utf-8') as f:
    f.write(content)
