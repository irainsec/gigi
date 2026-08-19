# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build CharacterStudio
#   Clones the repo, installs deps, fetches the default loot-assets pack,
#   and produces a production Vite bundle in /cs-build/dist
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS cs-builder

RUN apk add --no-cache git python3 make g++

WORKDIR /cs-build

# Clone CharacterStudio (pinned to main; swap to a tag for reproducible builds)
RUN git clone --depth=1 https://github.com/M3-org/CharacterStudio .

# Install all deps (incl. devDeps needed for Vite build)
RUN npm install --legacy-peer-deps

# Fetch the default loot-assets pack into public/
# This downloads the sample VRM traits so the editor has content out-of-the-box.
RUN npm run get-assets || true

# Build the Vite production bundle with base path /character-studio/ → build/
RUN VITE_ASSET_PATH=/character-studio npx vite build --base=/character-studio/

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Gigi Server (production image)
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine

WORKDIR /app

# Install server production dependencies only
COPY package*.json ./
RUN npm install --production --legacy-peer-deps

# Copy server code, Twigi render engine (server.js requires ./twigi_render_lpc)
# and Firebase key
COPY server.js live_routes.js plan_catalog.js app_settings.js twigi_render_lpc.js xbr.js ./
COPY serviceAccountKey.json* ./

# Twigi assets (LPC frames + manifest + credits). Also bind-mounted in compose so
# re-packs apply without a rebuild; this baked copy is the fallback.
COPY twigi_assets ./twigi_assets

# Copy existing public pages (privacy-policy, delete-account, index)
COPY public ./public

# ── CharacterStudio built assets ──────────────────────────────────────────────
# Served at  /character-studio/  by the Express static middleware.
# CharacterStudio's vite.config.js specifies outDir: './build'
COPY --from=cs-builder /cs-build/build ./public/character-studio

# Runtime directories (captures is bind-mounted in compose, but create it as
# a fallback so the server can start even without the volume)
RUN mkdir -p captures avatars notifications/icons

# Expose port
EXPOSE 2803

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "require('http').get('http://localhost:2803/healthz', (r) =>{process.exit(r.statusCode===200?0:1)})"

# Start
CMD ["node", "server.js"]
