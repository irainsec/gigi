import os

music_kt_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(music_kt_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("import androidx.compose.ui.zIndex.zIndex", "import androidx.compose.ui.zIndex")

with open(music_kt_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed zIndex import!")
