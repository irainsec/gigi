# WebSocket Server for Connected Screensaver

Simple Node.js WebSocket server for relaying scribbles between connected devices.

## Features

- ✅ Connection pairing via 8-character codes
- ✅ Binary scribble relay between partners
- ✅ Automatic cleanup of old connections
- ✅ Partner disconnect notifications
- ✅ Ping/pong heartbeat support

## Setup

### 1. Install Dependencies

```bash
cd websocket-server
npm install
```

### 2. Start Server

**Development (with auto-reload):**
```bash
npm run dev
```

**Production:**
```bash
npm start
```

The server will start on port **8080** by default.

## Configuration

### Change Port

Set the `PORT` environment variable:

```bash
PORT=3000 npm start
```

### Get Your Local IP

**Windows:**
```powershell
ipconfig
```
Look for "IPv4 Address" under your active network adapter (e.g., `192.168.1.100`)

**macOS/Linux:**
```bash
ifconfig
```
Look for `inet` address (e.g., `192.168.1.100`)

## Update Android App

Update the WebSocket URL in your Android app:

**File:** `app/src/main/java/com/aman/gigi/network/WebSocketClient.kt`

```kotlin
private val serverUrl = "ws://YOUR_LOCAL_IP:8080"
// Example: "ws://192.168.1.100:8080"
```

## Testing

### 1. Start Server
```bash
npm start
```

### 2. Install App on Two Devices
Both devices must be on the **same WiFi network** as your computer.

### 3. Test Connection Flow

**Device 1 (Creator):**
1. Open app → Screensaver tab
2. Tap "Create Connection"
3. Note the connection code (e.g., `ABCD-1234`)

**Device 2 (Joiner):**
1. Open app → Screensaver tab
2. Tap "Join Connection"
3. Enter the code from Device 1
4. Tap "Join"

**Both devices should now show "Connected"**

### 4. Test Drawing
1. On Device 1: Tap screen → Draw something → Tap "Send"
2. Device 2 should receive and animate the scribble
3. Try the reverse direction

## Server Logs

The server logs all activity:

```
🚀 WebSocket server started on port 8080
📡 Listening for connections...

✅ New client connected: a1b2c3d4-...
🔗 New connection created: ABCD-1234
✅ New client connected: e5f6g7h8-...
🤝 Connection paired: ABCD-1234
📤 Relayed scribble: 1234 bytes
👋 Client disconnected: a1b2c3d4-...
```

## Protocol

### Text Messages (JSON)

**Create Connection:**
```json
{
  "type": "create_connection",
  "connectionCode": "ABCD-1234",
  "deviceId": "device-uuid",
  "deviceName": "Device Name"
}
```

**Join Connection:**
```json
{
  "type": "join_connection",
  "connectionCode": "ABCD-1234",
  "deviceId": "device-uuid",
  "deviceName": "Device Name"
}
```

**Disconnect:**
```json
{
  "type": "disconnect"
}
```

**Ping:**
```json
{
  "type": "ping"
}
```

### Binary Messages

Compressed scribble data is sent as binary and automatically relayed to the partner.

## Troubleshooting

### "Connection refused" on Android

1. **Check firewall:** Allow port 8080 on your computer
2. **Verify IP:** Make sure you're using the correct local IP
3. **Same network:** Both devices and computer must be on same WiFi
4. **Server running:** Check that `npm start` is active

### "Connection not found"

The connection code might have expired (1 hour timeout). Create a new connection.

### Scribbles not appearing

1. Check server logs for "Relayed scribble" messages
2. Verify both devices show "Connected" status
3. Check Android logcat for errors

## Deployment (Optional)

For production use, deploy to a cloud service:

### Heroku
```bash
heroku create screensaver-ws
git push heroku main
```

### Railway
1. Connect GitHub repo
2. Deploy automatically
3. Get public URL (e.g., `wss://your-app.railway.app`)

### Update Android App
Change to secure WebSocket:
```kotlin
private val serverUrl = "wss://your-app.railway.app"
```

## License

MIT
