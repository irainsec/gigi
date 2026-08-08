import re

file_path = 'C:/Users/ATPL-ADMIN/.gemini/antigravity/brain/d0e44c4a-45ea-4017-b247-a264c16623a0/task.md'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('[/] Perform build tests to verify syntax and logic fixes', '[x] Perform build tests to verify syntax and logic fixes')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated task.md")
