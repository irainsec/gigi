
import os
path = r'c:/Users/ATPL-ADMIN/Downloads/gigi/gigi-server/server.js'
with open(path, 'rb') as f:
    content = f.read()

lines = content.split(b'\n')
# Line 647 is index 646
lines[646] = b'        const sessionCode = activeConnection.code || connectionCode;'
lines.insert(647, b'        const relativePath = path.join(sessionCode, filename);')

new_content = b'\n'.join(lines)
with open(path, 'wb') as f:
    f.write(new_content)
print("Replacement successful")
