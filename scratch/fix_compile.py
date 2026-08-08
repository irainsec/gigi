import os

music_kt_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(music_kt_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Add import androidx.compose.ui.zIndex.zIndex at the top
if "import androidx.compose.ui.zIndex.zIndex" not in content:
    content = content.replace("import androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.zIndex.zIndex")

# Fix 2: Remove .androidx.compose.ui.zIndex.zIndex(zIndex) and replace with .zIndex(zIndex)
content = content.replace(".androidx.compose.ui.zIndex.zIndex(zIndex)", ".zIndex(zIndex)")

# Fix 3: AlbumBrowserOverlay @Composable
# In fix_album_browser.py I did:
# }
# @Composable
# private fun AlbumBrowserOverlay(
# But if it wasn't matched properly, it didn't replace.
target_overlay = """}
private fun AlbumBrowserOverlay("""

replacement_overlay = """}
@Composable
private fun AlbumBrowserOverlay("""

if target_overlay in content:
    content = content.replace(target_overlay, replacement_overlay)

with open(music_kt_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Applied compile fixes!")
