# Docker & Cloudflare Deployment Guide

## 🐳 Docker Setup

### Option 1: Docker Compose (Recommended)

**1. Build and Start:**
```bash
cd gigi-server
docker-compose up -d
```

**2. View Logs:**
```bash
docker-compose logs -f
```

**3. Stop:**
```bash
docker-compose down
```

### Option 2: Docker CLI

**1. Build Image:**
```bash
docker build -t gigi-server .
```

**2. Run Container:**
```bash
docker run -d -p 6969:6969 --name gigi-server gigi-server
```

**3. View Logs:**
```bash
docker logs -f gigi-server
```

**4. Stop:**
```bash
docker stop gigi-server
docker rm gigi-server
```

---

## ☁️ Cloudflare Tunnel Setup

Cloudflare Tunnel allows you to expose your WebSocket server to the internet **without port forwarding** or a public IP!

### Prerequisites
- Cloudflare account (free)
- Domain name (can use Cloudflare's free subdomain)

### Step 1: Install Cloudflared

**Windows:**
```powershell
# Download from: https://github.com/cloudflare/cloudflared/releases
# Or use winget:
winget install --id Cloudflare.cloudflared
```

**macOS:**
```bash
brew install cloudflared
```

**Linux:**
```bash
wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared-linux-amd64.deb
```

### Step 2: Authenticate

```bash
cloudflared tunnel login
```

This opens a browser to authenticate with Cloudflare.

### Step 3: Create Tunnel

```bash
cloudflared tunnel create gigi-server
```

This creates:
- Tunnel ID (save this!)
- Credentials file: `~/.cloudflared/<TUNNEL_ID>.json`

### Step 4: Configure Tunnel

Create `config.yml` in `~/.cloudflared/` (or `C:\Users\<USER>\.cloudflared\` on Windows):

```yaml
tunnel: YOUR_TUNNEL_ID
credentials-file: C:\Users\ATPL-ADMIN\.cloudflared\YOUR_TUNNEL_ID.json

ingress:
  - hostname: screensaver.yourdomain.com
    service: http://localhost:6969
  - service: http_status:404
```

Replace:
- `YOUR_TUNNEL_ID` with your actual tunnel ID
- `screensaver.yourdomain.com` with your desired subdomain

### Step 5: Create DNS Record

```bash
cloudflared tunnel route dns gigi-server screensaver.yourdomain.com
```

### Step 6: Run Tunnel

**Foreground (testing):**
```bash
cloudflared tunnel run gigi-server
```

**Background (production):**
```bash
cloudflared service install
cloudflared service start
```

### Step 7: Update Android App

Edit `WebSocketClient.kt`:

```kotlin
companion object {
    // Use wss:// for secure WebSocket
    private const val SERVER_URL = "wss://screensaver.yourdomain.com"
}
```

---

## 🚀 Complete Deployment Flow

### Local Testing with Docker

```bash
# 1. Start Docker container
docker-compose up -d

# 2. Start Cloudflare Tunnel
cloudflared tunnel run gigi-server

# 3. Test from Android app using wss://screensaver.yourdomain.com
```

### Production Deployment

```bash
# 1. Start Docker container
docker-compose up -d

# 2. Install Cloudflare Tunnel as service
cloudflared service install
cloudflared service start

# 3. Server is now accessible globally at wss://screensaver.yourdomain.com
```

---

## 🔒 Security Benefits

✅ **No Port Forwarding** - No need to expose ports on your router  
✅ **Automatic HTTPS/WSS** - Cloudflare provides SSL/TLS  
✅ **DDoS Protection** - Cloudflare's network protects your server  
✅ **Access Control** - Can add Cloudflare Access for authentication  
✅ **Hidden Origin** - Your real IP stays hidden  

---

## 📊 Monitoring & Data Access

### 1. View Captured Assets (Scribbles/Images)
All binary data and images sent through the server are saved to the `./captures` folder on your host machine.
- **Path**: `gigi-server/captures/<connectionCode>/`
- **Traffic Log**: `gigi-server/captures/traffic.log` (Full history of all JSON traffic)

### 2. View Database Content (MongoDB)
All session data, partner pairings, and message histories are stored in MongoDB.
- **Tool**: You can use [MongoDB Compass](https://www.mongodb.com/products/tools/compass) to connect.
- **Connection String**: `mongodb://localhost:27017`
- **Database**: `screensaver`
- **Collections**: `sessions` (Contains history grouped by connection code)

### 3. Docker Logs
```bash
# View real-time server activity
docker-compose logs -f gigi-server
```

---

## 🧪 Testing

### Test WebSocket Connection

**Using wscat:**
```bash
npm install -g wscat
wscat -c wss://screensaver.yourdomain.com
```

**Send test message:**
```json
{"type":"ping"}
```

Should receive:
```json
{"type":"pong"}
```

---

## 💰 Cost

**Free Tier Includes:**
- ✅ Unlimited bandwidth
- ✅ Unlimited tunnels
- ✅ DDoS protection
- ✅ SSL/TLS certificates

**Perfect for personal projects!**

---

## 🔧 Troubleshooting

### Tunnel not connecting

```bash
# Check tunnel status
cloudflared tunnel info gigi-server

# Test tunnel
cloudflared tunnel run gigi-server
```

### WebSocket upgrade failing

Ensure `config.yml` has:
```yaml
originRequest:
  noTLSVerify: true
```

### DNS not resolving

Wait 5-10 minutes for DNS propagation, or flush DNS:
```bash
ipconfig /flushdns  # Windows
```

---

## 📱 Update Android App

**File:** `app/src/main/java/com/aman/gigi/network/WebSocketClient.kt`

```kotlin
@Singleton
class WebSocketClient @Inject constructor() {
    
    companion object {
        // Production: Use your Cloudflare Tunnel URL
        private const val SERVER_URL = "wss://screensaver.yourdomain.com"
        
        // Local testing: Use local IP
        // private const val SERVER_URL = "ws://10.253.137.132:6969"
    }
    
    fun connect(deviceId: String, listener: ConnectionListener) {
        // Use SERVER_URL constant
        val request = Request.Builder()
            .url(SERVER_URL)
            .addHeader("Device-Id", deviceId)
            .build()
        // ... rest of code
    }
}
```

---

**🎉 Your WebSocket server is now accessible globally!**
