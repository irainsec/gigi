# 🚀 Gigi Server with CharacterStudio

## What's included

- **gigi-server** — Node.js/Express WebSocket backend for the Gigi app
- **CharacterStudio** — M3-org VRM avatar editor, **built and embedded automatically** during `docker compose build`

## Quick Start

```bash
# 1. Copy and fill in your secrets
cp .env.example .env

# 2. Build + start everything (first build takes ~5–10 min: downloads + builds CharacterStudio)
docker compose build
docker compose up -d

# 3. CharacterStudio is now live at:
#    https://<your-domain>/character-studio/
#    (or http://localhost:2803/character-studio/ locally)
```

## How CharacterStudio gets bundled

The `Dockerfile` uses a **multi-stage build**:

| Stage | What it does |
|-------|-------------|
| `cs-builder` | Clones `M3-org/CharacterStudio`, runs `npm install`, `npm run get-assets` (loot-assets), `npm run build` → produces `dist/` |
| Final image | Copies `dist/` → `/app/public/character-studio/` which Express serves at `/character-studio/` |

The Android app (`TwigiCharacterStudioDialog`) automatically loads `https://<server>/character-studio/` based on `BuildConfig.SERVER_URL`.

## Updating CharacterStudio assets

**Option A — Full rebuild (recommended)**
```bash
docker compose build --no-cache gigi-server
docker compose up -d gigi-server
```

**Option B — Manual asset update (no rebuild)**
```bash
# Build locally
cd /tmp && git clone https://github.com/M3-org/CharacterStudio && cd CharacterStudio
npm install --legacy-peer-deps && npm run get-assets && npm run build

# Copy into the server's public folder
cp -r dist/* /path/to/gigi-server/public/character-studio/

# If using the volume mount, just restart the container
docker compose restart gigi-server
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `CHARACTER_STUDIO_URL` | `/character-studio/` | URL shown to Android clients |
| `ADMIN_USERNAME` | *(required)* | Admin panel login |
| `ADMIN_PASSWORD` | *(required)* | Admin panel password |
| `TUNNEL_TOKEN` | *(required)* | Cloudflare tunnel token |
| `MINIO_ROOT_USER` | `admin` | MinIO access key |
| `MINIO_ROOT_PASSWORD` | `password123` | MinIO secret key |

## Architecture

```
docker compose up
├── gigi-server   :2803  — Node.js WebSocket + REST API + static files
│   ├── /                     → public/index.html
│   ├── /character-studio/    → CharacterStudio VRM editor (built into image)
│   ├── /captures/            → User content (bind-mounted)
│   └── /api/                 → REST endpoints
├── gigi-db       :6970  — MongoDB (loopback only)
├── gigi-db-ui    :6971  — Mongo Express (loopback only)
├── minio         :9000  — S3-compatible object storage (loopback only)
│   └── minio-console :9001
└── cloudflared          — Cloudflare tunnel (exposes :2803 publicly)
```
