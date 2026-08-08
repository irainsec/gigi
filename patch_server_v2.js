const fs = require('fs');
const filePath = 'c:/Users/ATPL-ADMIN/Downloads/gigi/gigi-server/server.js';
let content = fs.readFileSync(filePath, 'utf8');

// Target the relayTextMessage function
const relayPattern = /function relayTextMessage\(ws, message\) \{([\s\S]+?)connection\.clients\.forEach\(partnerWs => \{([\s\S]+?)if \(partnerWs !== ws && partnerWs\.readyState === WebSocket\.OPEN\) \{([\s\S]+?)const partnerClient = clients\.get\(partnerWs\);/m;

if (relayPattern.test(content)) {
    content = content.replace(relayPattern, (match, p1, p2, p3) => {
        return `function relayTextMessage(ws, message) {${p1}const recipientDeviceId = message.recipientDeviceId || message.targetDeviceId || message.payload?.targetDeviceId;
    connection.clients.forEach(partnerWs => {${p2}if (partnerWs !== ws && partnerWs.readyState === WebSocket.OPEN) {${p3}const partnerClient = clients.get(partnerWs);
            if (!partnerClient) return;
            if (recipientDeviceId && partnerClient.deviceId !== recipientDeviceId) return;`;
    });
    fs.writeFileSync(filePath, content);
    console.log('✅ Successfully patched relayTextMessage in server.js');
} else {
    console.error('❌ Could not find relayTextMessage pattern in server.js');
}
