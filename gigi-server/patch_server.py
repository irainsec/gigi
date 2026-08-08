
import os
path = r'c:/Users/ATPL-ADMIN/Downloads/gigi/gigi-server/server.js'
with open(path, 'rb') as f:
    content = f.read()

# Replace the block at lines 624-632
lines = content.split(b'\n')
# Lines are 0-indexed, so 624-632 is 623 to 631
# We want to replace lines 624 (index 623) to 632 (index 631)

new_block = b"""        const connectionCode = req.body.connectionCode;
        const connectionId = req.body.connectionId;

        // SECURITY: Verify connection is active (check both code and ID)
        let activeConnection = connections.get(connectionCode);
        if (!activeConnection && connectionId) {
            // Fallback: search by connectionId
            for (const [code, conn] of connections.entries()) {
                if (conn.connectionId === connectionId) {
                    activeConnection = conn;
                    break;
                }
            }
        }

        if (!activeConnection) {
            // Delete the unauthorized file immediately
            if (req.file) fs.unlinkSync(req.file.path);
            console.warn(`[UPLOAD] Rejected upload for invalid/inactive connection: ${connectionCode || connectionId}`);
            return res.status(403).json({ error: 'Invalid or inactive connection code/ID' });
        }"""

# Join back with same line endings
lines[623:632] = [new_block]
new_content = b'\n'.join(lines)

with open(path, 'wb') as f:
    f.write(new_content)
print("Replacement successful")
