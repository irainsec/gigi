# WebSocket Server - Quick Start Guide

## 🚀 Setup (5 minutes)

### Step 1: Install Dependencies

```powershell
cd websocket-server
npm install
```

### Step 2: Start Server

```powershell
npm start
```

You should see:
```
🚀 WebSocket server started on port 8080
📡 Listening for connections...
```

### Step 3: Get Your Local IP Address

**Windows:**
```powershell
ipconfig
```

Look for **"IPv4 Address"** under your WiFi adapter (e.g., `192.168.1.100`)

### Step 4: Update Android App

Edit: `app/src/main/java/com/aman/gigi/network/WebSocketClient.kt`

Find line 31 and update the URL in the `connect()` method calls to use your local IP:

**Example:**
```kotlin
// Before: ws://localhost:8080
// After:  ws://192.168.1.100:8080
```

Or add a constant at the top of the file:
```kotlin
companion object {
    private const val SERVER_URL = "ws://192.168.1.100:8080"
}
```

### Step 5: Rebuild App

```powershell
cd ..
./gradlew assembleDebug
```

### Step 6: Install on Two Devices

```powershell
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Testing (End-to-End)

### Prerequisites
- ✅ Server running (`npm start`)
- ✅ Both devices on **same WiFi** as your computer
- ✅ App installed on both devices

### Test Flow

**Device 1 (Creator):**
1. Open app → **Screensaver** tab
2. Tap **"Create Connection"**
3. Note the code (e.g., `ABCD-1234`)
4. Share code with Device 2

**Device 2 (Joiner):**
1. Open app → **Screensaver** tab
2. Tap **"Join Connection"**
3. Enter code: `ABCD-1234`
4. Tap **"Join"**

**Both devices should show:**
- ✅ "Connected" status
- ✅ Partner name
- ✅ Current time

**Test Drawing:**
1. **Device 1:** Tap screen → Draw → Tap "Send"
2. **Device 2:** Should receive and animate the scribble
3. **Device 2:** Tap screen → Draw → Tap "Send"
4. **Device 1:** Should receive and animate

---

## 📊 Server Logs

Watch the server console for activity:

```
✅ New client connected: a1b2c3d4-...
🔗 New connection created: ABCD-1234
✅ New client connected: e5f6g7h8-...
🤝 Connection paired: ABCD-1234
📤 Relayed scribble: 1234 bytes
```

---

## 🐛 Troubleshooting

### "Connection refused" on Android

**Solution:**
1. Check Windows Firewall - allow port 8080
2. Verify server is running (`npm start`)
3. Confirm correct IP address in app
4. Both devices on same WiFi network

### Scribbles not appearing

**Solution:**
1. Check server logs for "Relayed scribble" messages
2. Verify both devices show "Connected"
3. Check Android logcat: `adb logcat | grep Scribble`

### Connection code not working

**Solution:**
- Codes expire after 1 hour
- Create a new connection

---

## 🎯 What to Test

- [ ] Create connection generates QR code
- [ ] Join via QR scan works
- [ ] Join via manual code works
- [ ] Both devices show "Connected"
- [ ] Drawing on Device 1 appears on Device 2
- [ ] Drawing on Device 2 appears on Device 1
- [ ] Stroke-by-stroke animation plays
- [ ] Fade-out effect works
- [ ] Multiple scribbles queue properly
- [ ] Disconnect works
- [ ] Settings screen shows connection info

---

## 🌐 Deploy to Cloud (Optional)

For testing across different networks, deploy to Railway/Heroku:

**Railway (Recommended):**
1. Push code to GitHub
2. Connect repo to Railway
3. Deploy automatically
4. Get URL: `wss://your-app.railway.app`

**Update app to use secure WebSocket:**
```kotlin
private const val SERVER_URL = "wss://your-app.railway.app"
```

---

**Ready to test! 🎉**
