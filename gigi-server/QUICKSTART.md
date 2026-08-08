# WebSocket Server - Quick Commands

## 🐳 Docker

```bash
# Build and start
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down

# Rebuild after code changes
docker-compose up -d --build
```

## ☁️ Cloudflare Tunnel

```bash
# Install (Windows)
winget install --id Cloudflare.cloudflared

# Login
cloudflared tunnel login

# Create tunnel
cloudflared tunnel create screensaver-ws

# Configure DNS
cloudflared tunnel route dns screensaver-ws screensaver.yourdomain.com

# Run tunnel (testing)
cloudflared tunnel run screensaver-ws

# Install as service (production)
cloudflared service install
cloudflared service start
```

## 🧪 Testing

```bash
# Test WebSocket
npm install -g wscat
wscat -c wss://screensaver.yourdomain.com

# Send ping
{"type":"ping"}
```

## 📱 Android App Update

**File:** `app/src/main/java/com/aman/gigi/network/WebSocketClient.kt`

```kotlin
companion object {
    private const val SERVER_URL = "wss://screensaver.yourdomain.com"
}
```

Then rebuild:
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
