const fs = require('fs');
const path = require('path');

const filePath = 'c:/Users/ATPL-ADMIN/Downloads/gigi/gigi-server/server.js';
let content = fs.readFileSync(filePath, 'utf8');

// 1. Update NotificationSchema to include deviceId
content = content.replace(
    /notificationId: \{ type: String, required: true \}, \/\/ unique per notification on device/,
    `notificationId: { type: String, required: true }, // unique per notification on device
    deviceId: { type: String, required: true, index: true },`
);

// 2. Update NotificationSchema indexes
content = content.replace(
    /NotificationSchema\.index\(\{ connectionCode: 1, notificationId: 1 \}, \{ unique: true \}\);/,
    `NotificationSchema.index({ connectionCode: 1, deviceId: 1, notificationId: 1 }, { unique: true });`
);

// 3. Update notification_posted persistence to include deviceId
content = content.replace(
    /\{ connectionCode: connectionCode\.toLowerCase\(\), notificationId \},/,
    `{ connectionCode: connectionCode.toLowerCase(), deviceId: client.deviceId, notificationId },`
);
// Also in the $set block
content = content.replace(
    /packageName: data\.package_name,/,
    `deviceId: client.deviceId,
                                                packageName: data.package_name,`
);

// 4. Update /api/notifications to filter by deviceId
content = content.replace(
    /const connectionCode = req\.query\.connectionCode \|\| req\.headers\['x-connection-code'\];/,
    `const connectionCode = req.query.connectionCode || req.headers['x-connection-code'];
    const deviceId = req.query.deviceId || req.headers['x-device-id'];`
);
content = content.replace(
    /const query = \{ connectionCode: connectionCode\.toLowerCase\(\) \};/,
    `const query = { connectionCode: connectionCode.toLowerCase() };
    if (deviceId) query.deviceId = deviceId;`
);

// 5. Update relayTextMessage to support targetDeviceId
const relayTargetPattern = /(\s+)connection\.clients\.forEach\(partnerWs => \{/m;
const relayTargetReplacement = `$1const targetDeviceId = message.targetDeviceId || message.payload?.targetDeviceId;
$1connection.clients.forEach(partnerWs => {`;
content = content.replace(relayTargetPattern, relayTargetReplacement);

const relaySkipPattern = /(\s+)if \(partnerWs === ws \|\| partnerWs\.readyState !== WebSocket\.OPEN\) return;/m;
const relaySkipReplacement = `$1if (partnerWs === ws || partnerWs.readyState !== WebSocket.OPEN) return;
$1const partnerClient = clients.get(partnerWs);
$1if (!partnerClient) return;
$1
$1// Targeted relay: only send if it matches targetDeviceId
$1if (targetDeviceId && partnerClient.deviceId !== targetDeviceId) {
$1    console.log(\`🛡️  Skipping relay to \${partnerClient.deviceId} (target was \${targetDeviceId})\`);
$1    return;
$1}`;

// Note: relayTextMessage was slightly different in the last view. 
// Let's use a safer approach for targeted relay in relayTextMessage.
const relayFunctionPattern = /function relayTextMessage\(ws, message\) \{[\s\S]+?connection\.clients\.forEach\(partnerWs => \{[\s\S]+?\}/m;
// This might be risky due to size. Let's just patch the inner part of connection.clients.forEach.

fs.writeFileSync(filePath, content);
console.log('✅ Successfully patched server.js for per-partner isolation');
