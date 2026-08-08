
const fs = require('fs');
const content = fs.readFileSync('c:/Users/ATPL-ADMIN/Downloads/gigi/gigi-server/server.js', 'utf8');
const lines = content.split('\n');
console.log('Line 624 (raw):', JSON.stringify(lines[623]));
