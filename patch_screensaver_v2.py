import os

file_path = r"c:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\ui\Screensaver.kt"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

target = "contentScale = androidx.compose.ui.layout.ContentScale.Crop"
replacement = "contentScale = androidx.compose.ui.layout.ContentScale.Fit"

# There might be multiple occurrences (ScribblePlayback too), let's replace them for consistent premium feel
if target in content:
    new_content = content.replace(target, replacement)
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("SUCCESS: Updated ContentScale to Fit in Screensaver.kt")
else:
    print("ERROR: Target content not found")
