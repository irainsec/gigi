import os
import re

music_kt_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(music_kt_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix @Composable for AlbumBrowserOverlay
content = re.sub(r'\}\n\nprivate fun AlbumBrowserOverlay\(', '}\n\n@Composable\nprivate fun AlbumBrowserOverlay(', content)

with open(music_kt_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Added @Composable to AlbumBrowserOverlay")
