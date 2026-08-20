const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');
const mongoose = require('mongoose');
const fs = require('fs');
const path = require('path');
const express = require('express');
let twigiRender;
try {
    twigiRender = require('./twigi_render_lpc');
} catch (e) {
    console.warn('⚠️ twigi_render_lpc not present — using fallback stub');
    twigiRender = {
        CATALOG: { parts: {}, default: {} },
        renderTwigi: async () => null,
        renderTwigiGif: async () => null,
        configHash: () => '',
        isAnimated: () => false
    };
}
const admin = require('firebase-admin');
let serviceAccount;
try {
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
        serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    } else {
        serviceAccount = require('./serviceAccountKey.json');
    }
    
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
} catch (error) {
    console.warn('⚠️ Failed to initialize Firebase:', error.message);
}

const zlib = require('zlib');
const crypto = require('crypto');

const PORT = process.env.PORT || 6969;
const MONGO_BASE_URL = process.env.MONGO_BASE_URL || 'mongodb://127.0.0.1:6970';
const DOWNLOADS_DIR = path.join(__dirname, 'downloads');

const SCREENSAVER_DB_URL = `${MONGO_BASE_URL}/screensaver`;
const GIGI_DB_URL = `${MONGO_BASE_URL}/gigi`;
const PROTOCOL_VERSION = 2;
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || '';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '';
const LOG_LEVEL = process.env.LOG_LEVEL || 'info';
const ACTION_RETENTION_MS = 14 * 24 * 60 * 60 * 1000;
const SERVER_STATE_KEY = 'global';
const MAINTENANCE_CLOSE_DELAY_MS = 1500;
const OTP_TTL_MS = Number(process.env.OTP_TTL_MS || 5 * 60 * 1000);
const AUTH_SESSION_TTL_MS = Number(process.env.AUTH_SESSION_TTL_MS || 90 * 24 * 60 * 60 * 1000);
const OTP_DEV_MODE = String(process.env.OTP_DEV_MODE || 'true').toLowerCase() !== 'false';

// Multiple Mongoose Connections
const screensaverConn = mongoose.createConnection(SCREENSAVER_DB_URL);
const gigiConn = mongoose.createConnection(GIGI_DB_URL);

screensaverConn.on('connected', () => console.log('🍃 Screensaver DB (Sessions) Connected'));
screensaverConn.on('error', err => console.error('💥 Screensaver DB Error:', err.message));

gigiConn.on('connected', () => console.log('🛡️  GIGI DB (Devices) Connected'));
gigiConn.on('error', err => console.error('💥 GIGI DB Error:', err.message));

// Express Setup
const app = express();
const server = require('http').createServer(app);

app.use((req, res, next) => {
    if (req.url.startsWith('/captures/') || req.url.startsWith('/avatars/')) {
        console.log(`≡ƒôÑ File Request: ${req.url} from ${req.ip}`);
    }
    next();
});

app.use(express.json({ limit: '6mb' }));

// ── Rate limiting ────────────────────────────────────────────────────────────
// The server sits behind a Cloudflare tunnel, so the real client IP arrives in
// the CF-Connecting-IP header; fall back to the socket IP for direct access.
const { rateLimit: expressRateLimit, ipKeyGenerator } = require('express-rate-limit');
const clientIpKey = (req) => ipKeyGenerator(req.headers['cf-connecting-ip'] || req.ip || '');

const apiLimiter = expressRateLimit({
    windowMs: 60 * 1000,
    limit: 300,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    keyGenerator: clientIpKey,
    message: { error: 'Too many requests. Please slow down.' }
});

// Strict limiter for credential endpoints (OTP brute force / SMS abuse, admin login)
const authLimiter = expressRateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 10,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    keyGenerator: clientIpKey,
    message: { error: 'Too many attempts. Please try again later.' }
});

app.use('/api/', apiLimiter);
app.use(['/api/auth/request-otp', '/api/auth/verify-otp', '/api/auth/request-account-deletion', '/admin/login'], authLimiter);

// Public landing page at "/". The admin console keeps living at /admin
app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'public', 'landing.html')));
app.get('/admin', (req, res) => res.sendFile(path.join(__dirname, 'public', 'index.html')));
app.use(express.static(path.join(__dirname, 'public'), { index: false }));

// Direct APK distribution (before/alongside the Play Store). Drop a new build in
// downloads/ with tools/publish_apk.ps1 and users get it from the site immediately.
app.get(['/download', '/downloads'], (req, res) => {
    let latestMeta = {};
    try {
        latestMeta = JSON.parse(fs.readFileSync(path.join(DOWNLOADS_DIR, 'latest.json'), 'utf8'));
    } catch (_) {}
    const ver = latestMeta.versionName || 'v1.6.0';
    const size = latestMeta.sizeMb ? `${latestMeta.sizeMb} MB` : '93 MB';
    
    res.send(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Download Gigi ${ver} ✨</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: linear-gradient(135deg, #181028 0%, #2b1845 50%, #432168 100%);
            color: #ffffff; min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px;
        }
        .card {
            background: rgba(255, 255, 255, 0.08); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
            border: 1.5px solid rgba(255, 255, 255, 0.18); border-radius: 32px;
            max-width: 440px; width: 100%; padding: 36px 28px; text-align: center;
            box-shadow: 0 20px 50px rgba(0,0,0,0.4);
        }
        .app-icon {
            width: 96px; height: 96px; border-radius: 24px; margin: 0 auto 16px;
            background: linear-gradient(135deg, #a855f7, #ec4899); display: flex; align-items: center; justify-content: center;
            font-size: 48px; box-shadow: 0 10px 25px rgba(168, 85, 247, 0.4);
        }
        h1 { font-size: 26px; font-weight: 800; margin-bottom: 6px; letter-spacing: -0.5px; }
        .tagline { color: #d8b4fe; font-size: 14px; margin-bottom: 20px; font-weight: 500; }
        .badge {
            display: inline-block; background: rgba(168, 85, 247, 0.25); border: 1px solid rgba(168, 85, 247, 0.5);
            color: #f3e8ff; padding: 6px 14px; border-radius: 20px; font-size: 12px; font-weight: 700; margin-bottom: 24px;
        }
        .btn-download {
            display: flex; align-items: center; justify-content: center; gap: 10px;
            background: linear-gradient(135deg, #a855f7 0%, #7c3aed 100%);
            color: #ffffff; text-decoration: none; font-size: 17px; font-weight: 700;
            padding: 16px 28px; border-radius: 99px; width: 100%;
            box-shadow: 0 8px 25px rgba(124, 58, 237, 0.5); transition: transform 0.2s, box-shadow 0.2s;
        }
        .btn-download:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(124, 58, 237, 0.6); }
        .notes { margin-top: 24px; font-size: 12px; color: #a78bfa; line-height: 1.5; text-align: left; background: rgba(0,0,0,0.2); padding: 14px; border-radius: 16px; }
    </style>
</head>
<body>
    <div class="card">
        <div class="app-icon">🌻</div>
        <h1>Gigi ${ver}</h1>
        <div class="tagline">Your shared galaxy & screensaver 💕</div>
        <div class="badge">Android APK • ${size}</div>
        <a href="/downloads/gigi-latest.apk" class="btn-download">
            <span>🚀 Download Latest APK</span>
        </a>
        <div class="notes">
            <strong>✨ What's New:</strong><br>
            • Offline 2D Twigi Studio with asset catalog & auto server sync<br>
            • Dynamic Island Liquid Music Player<br>
            • Twigi VIP Paywall Manager & try-on system<br>
            • Performance & stability enhancements
        </div>
    </div>
</body>
</html>`);
});

// Serves the universal APK plus the per-ABI builds the in-app updater prefers
// (gigi-arm64-v8a.apk and friends — roughly half the size of the universal one).
// The filename is matched against a fixed set rather than taken from the path, so
// this can never be walked out of DOWNLOADS_DIR.
// Two shapes of APK filename are served:
//
//   gigi-latest.apk / gigi-<abi>.apk        stable aliases — contents change per
//                                           release, so they must NOT be cached hard
//   gigi-<version>-<abi>.apk                immutable — a given name is always the
//                                           same bytes, so it can live at the edge
//                                           for a year
//
// The in-app updater is pointed at the immutable names. That is what lets Cloudflare
// actually hold the file: previously every phone pulled 25 MB from this machine over a
// residential uplink, because a mutable `gigi-arm64-v8a.apk` can't safely be cached.
const STABLE_APKS = new Set([
    'gigi-latest.apk',
    'gigi-arm64-v8a.apk', 'gigi-armeabi-v7a.apk', 'gigi-x86.apk', 'gigi-x86_64.apk'
]);
const VERSIONED_APK = /^gigi-v?[0-9][0-9A-Za-z._-]*-(arm64-v8a|armeabi-v7a|x86_64|x86|universal)\.apk$/;

app.all(['/download/:file', '/downloads/:file'], (req, res, next) => {
    const file = req.params.file;
    const stable = STABLE_APKS.has(file);
    const versioned = VERSIONED_APK.test(file);
    if (!stable && !versioned) return next();

    const apk = path.resolve(path.join(DOWNLOADS_DIR, file));
    if (!apk.startsWith(path.resolve(DOWNLOADS_DIR))) return res.status(400).end();
    if (!fs.existsSync(apk)) return res.status(404).send('No build published yet.');

    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', `attachment; filename="${file}"`);
    res.setHeader('Cache-Control', versioned
        ? 'public, max-age=31536000, immutable'
        : 'public, max-age=300');

    if (req.method === 'HEAD') {
        // Only set Content-Length ourselves on HEAD. On GET, sendFile owns it —
        // presetting the full size breaks 206 responses, whose body is shorter.
        res.setHeader('Accept-Ranges', 'bytes');
        res.setHeader('Content-Length', fs.statSync(apk).size);
        return res.status(200).end();
    }
    // sendFile handles Range/206 and sets Accept-Ranges itself.
    res.sendFile(apk);
});

// ── Map tile proxy ────────────────────────────────────────────────────────────
// The app used to hit tile.openstreetmap.org directly from every phone. That is slow
// (origin round trip per tile, ~0.5-2s) and outside OSM's tile usage policy, which
// does not permit app-scale direct use. Going through here means Cloudflare edge-caches
// each tile, users get a nearby PoP instead of OSM's origin, and OSM sees one polite
// server with a real User-Agent rather than a swarm of handsets.
const TILE_UPSTREAM = process.env.TILE_UPSTREAM || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const tileMemo = new Map();                 // z/x/y -> Buffer, small hot cache
const TILE_MEMO_MAX = 500;

app.get('/tiles/:z/:x/:y.png', async (req, res) => {
    const z = parseInt(req.params.z, 10);
    const x = parseInt(req.params.x, 10);
    const y = parseInt(req.params.y, 10);
    const n = 2 ** z;
    if (!Number.isInteger(z) || z < 0 || z > 19 ||
        !Number.isInteger(x) || !Number.isInteger(y) ||
        x < 0 || y < 0 || x >= n || y >= n) {
        return res.status(400).send('Bad tile');
    }

    const key = `${z}/${x}/${y}`;
    // Tiles are effectively immutable, so cache hard and let the edge do the work.
    res.setHeader('Content-Type', 'image/png');
    res.setHeader('Cache-Control', 'public, max-age=2592000, immutable');

    const hot = tileMemo.get(key);
    if (hot) return res.end(hot);

    try {
        const upstream = TILE_UPSTREAM
            .replace('{z}', z).replace('{x}', x).replace('{y}', y);
        const r = await fetch(upstream, {
            headers: { 'User-Agent': 'GigiServer/1.0 (+https://gigi.iamanraj.com; aman.raj@alticyber.com)' }
        });
        if (!r.ok) return res.status(502).send('Tile upstream error');
        const buf = Buffer.from(await r.arrayBuffer());
        if (tileMemo.size >= TILE_MEMO_MAX) tileMemo.delete(tileMemo.keys().next().value);
        tileMemo.set(key, buf);
        res.end(buf);
    } catch (e) {
        res.status(502).send('Tile fetch failed');
    }
});

app.get(['/download/latest.json', '/downloads/latest.json'], (req, res) => {
    const meta = path.resolve(path.join(DOWNLOADS_DIR, 'latest.json'));
    if (!fs.existsSync(meta)) return res.json({});
    res.type('application/json').sendFile(meta);
});


// Invite links: /join?code=XXXXXXXX — verified app-links open Gigi directly; this page
// is the fallback for browsers / people who don't have the app yet.
app.get('/join', (req, res) => res.sendFile(path.join(__dirname, 'public', 'join.html')));

// Android App Links verification — lets https://gigi.iamanraj.com/join open the app
// directly (no browser hop). Must be served as application/json, no redirect.
app.get('/.well-known/assetlinks.json', (req, res) => {
    res.type('application/json')
        .sendFile(path.join(__dirname, 'public', '.well-known', 'assetlinks.json'));
});

app.use('/captures', express.static(path.join(__dirname, 'captures')));
app.use('/app/captures', express.static(path.join(__dirname, 'captures')));
app.use('/avatars', express.static(path.join(__dirname, 'avatars')));
app.use('/notifications/icons', express.static(path.join(__dirname, 'notifications', 'icons')));

// ── CharacterStudio VRM avatar editor ────────────────────────────────────────
// Built assets are copied to public/character-studio/ by the multi-stage
// Dockerfile.  We serve them under /character-studio/ with permissive CORS so
// the Android WebView (different origin) can load and postMessage back.
const CS_DIR = path.join(__dirname, 'public', 'character-studio');

// Route all static sub-folders required by CharacterStudio (assets, 3d, fonts, hdr, icons, ktx2, sound, textures, ui, etc.)
const csSubDirs = [
  'assets', 'ktx2', 'ui', 'loot-assets', 'lora-assets', '3d',
  'fonts', 'hdr', 'icons', 'scripts', 'sound', 'sprite-atlas-assets',
  'textures', 'thumbnail-assets'
];
csSubDirs.forEach(sub => {
  const subPath = path.join(CS_DIR, sub);
  app.use(`/${sub}`, express.static(subPath));
  app.use(`/character-studio/${sub}`, express.static(subPath));
});

// Explicit route for manifest.json required by CharacterStudio
app.use('/manifest.json', (req, res) => {
  const manifestPath = path.join(CS_DIR, 'manifest.json');
  if (fs.existsSync(manifestPath)) {
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.sendFile(manifestPath);
  } else {
    res.status(404).json({ error: 'manifest.json not found' });
  }
});

// Explicit route for both /character-studio and /character-studio/
app.get(['/character-studio', '/character-studio/'], (req, res) => {
  const indexPath = path.join(CS_DIR, 'index.html');
  if (fs.existsSync(indexPath)) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'no-cache');
    res.sendFile(indexPath);
  } else {
    res.status(503).json({
      error: 'CharacterStudio assets not built yet',
      hint: 'Rebuild the Docker image to include CharacterStudio'
    });
  }
});

app.use('/character-studio', (req, res, next) => {
  // Allow embedding from any origin (Android WebView has null origin)
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Cross-Origin-Embedder-Policy', 'credentialless');
  res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
  res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  if (req.path === '/' || req.path === '/index.html' || req.path === '') {
    res.setHeader('Cache-Control', 'no-cache');
  } else {
    res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
  }
  next();
}, express.static(CS_DIR, { index: 'index.html', fallthrough: true }));

// SPA fallback: any /character-studio/* path that doesn't match a file
// returns index.html so Vite's client-side router works correctly.
app.get('/character-studio/*', (req, res) => {
  const indexPath = path.join(CS_DIR, 'index.html');
  if (fs.existsSync(indexPath)) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'no-cache');
    res.sendFile(indexPath);
  } else {
    res.status(503).json({
      error: 'CharacterStudio assets not built yet',
      hint: 'Rebuild the Docker image to include CharacterStudio'
    });
  }
});

const CAPTURES_DIR = path.join(__dirname, 'captures');
const AVATARS_DIR = path.join(__dirname, 'avatars');
const NOTIFICATIONS_DIR = path.join(__dirname, 'notifications');
const NOTIFICATION_ICONS_DIR = path.join(NOTIFICATIONS_DIR, 'icons');

// Helper to ensure directories exist
[CAPTURES_DIR, AVATARS_DIR, NOTIFICATIONS_DIR, NOTIFICATION_ICONS_DIR].forEach(dir => {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
        console.log(`📁 Created directory: ${dir}`);
    }
});


// MinIO Client setup
const Minio = require('minio');
const minioClient = new Minio.Client({
    endPoint: process.env.MINIO_ENDPOINT || 'minio',
    port: parseInt(process.env.MINIO_PORT || '9000'),
    useSSL: false,
    accessKey: process.env.MINIO_ACCESS_KEY || 'admin',
    secretKey: process.env.MINIO_SECRET_KEY || 'password123'
});
const BUCKET_NAME = process.env.MINIO_BUCKET || 'gigi-storage';

// Ensure bucket exists
minioClient.bucketExists(BUCKET_NAME, (err, exists) => {
    if (err || !exists) {
        minioClient.makeBucket(BUCKET_NAME, 'us-east-1', (err) => {
            if (err) {
                console.error('❌ MinIO error making bucket:', err.message);
            } else {
                console.log('📁 Created MinIO bucket:', BUCKET_NAME);
                setLifecycle();
            }
        });
    } else {
        console.log('✅ MinIO bucket exists:', BUCKET_NAME);
        setLifecycle();
    }
});

function setLifecycle() {
    const lifecycleConfig = {
        Rule: [{
            ID: 'Expire-Old-Files',
            Status: 'Enabled',
            Expiration: { Days: 1 },
            Filter: { Prefix: '' }
        }]
    };
    minioClient.setBucketLifecycle(BUCKET_NAME, lifecycleConfig, (err) => {
        if (err) console.error('❌ MinIO bucket lifecycle error:', err.message);
        else console.log('⏳ Set MinIO bucket 24-hour expiration lifecycle successfully');
    });
}

// Pre-signed upload endpoint
app.get('/api/storage/presigned-upload-url', async (req, res) => {
    const auth = await requireAuthenticatedMember(req, res);
    if (!auth) return;

    const { fileName } = req.query;
    if (!fileName) return res.status(400).json({ error: 'fileName query param required' });
    minioClient.presignedPutUrl(BUCKET_NAME, fileName, 24 * 60 * 60, (err, url) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ url });
    });
});

// Pre-signed download endpoint
app.get('/api/storage/presigned-download-url', async (req, res) => {
    const auth = await requireAuthenticatedMember(req, res);
    if (!auth) return;

    const { fileName } = req.query;
    if (!fileName) return res.status(400).json({ error: 'fileName query param required' });
    minioClient.presignedGetUrl(BUCKET_NAME, fileName, 24 * 60 * 60, (err, url) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ url });
    });
});

// API Endpoints

// Store active connections by connection code
const connections = new Map();
// Store client info
const clients = new Map();

// --- CONNECTION LIFECYCLE: Ping/Pong & Idle Timeout ---
const PING_INTERVAL = 30 * 1000;
const PONG_TIMEOUT = 90 * 1000; // Expect pong within 90 seconds to allow slow clients to respond
const IDLE_TIMEOUT = 30 * 60 * 1000; // Close connection after 30 minutes of inactivity
const IDLE_CHECK_INTERVAL = 5 * 60 * 1000; // Check for idle connections every 5 minutes
const PRESENCE_STALE_MS = 45 * 1000;

const metrics = {
    reconnectCount: 0,
    retryableFailureCount: 0,
    permanentFailureCount: 0
};

let cachedServerState = {
    mode: 'ONLINE',
    message: '',
    updatedAt: new Date()
};

function logEvent(event, fields = {}) {
    const payload = {
        timestamp: new Date().toISOString(),
        level: LOG_LEVEL,
        event,
        ...fields
    };
    console.log(JSON.stringify(payload));
}

function safeTiming(startedAt) {
    return Math.max(0, Date.now() - startedAt);
}

const DASHBOARD_REMOTE_COMMANDS = new Set([
    'get_location',
    'take_remote_photo',
    'get_photo_list',
    'get_full_photo',
    'get_file_list',
    'get_file_data'
]);

const PROFILE_GENDER_VALUES = new Set(['him', 'her', 'them']);

function toIsoTimestamp(value) {
    const date = value ? new Date(value) : new Date();
    return Number.isNaN(date.getTime()) ? new Date().toISOString() : date.toISOString();
}

function toTimestampMs(value) {
    const date = value ? new Date(value) : new Date();
    return Number.isNaN(date.getTime()) ? Date.now() : date.getTime();
}

function truncateString(value, maxLength = 320) {
    const text = String(value ?? '');
    if (text.length <= maxLength) return text;
    return `${text.slice(0, maxLength - 1)}…`;
}

function compactArchiveData(value, depth = 0) {
    if (value == null) return value;
    if (typeof value === 'string') return truncateString(value, depth === 0 ? 4000 : 1200);
    if (typeof value === 'number' || typeof value === 'boolean') return value;
    if (value instanceof Date) return value.toISOString();
    if (Buffer.isBuffer(value)) return `[buffer ${value.length}b]`;
    if (depth >= 3) return '[truncated]';
    if (Array.isArray(value)) {
        return value.slice(0, 30).map(entry => compactArchiveData(entry, depth + 1));
    }
    if (typeof value === 'object') {
        return Object.entries(value)
            .slice(0, 30)
            .reduce((acc, [key, entry]) => {
                acc[key] = compactArchiveData(entry, depth + 1);
                return acc;
            }, {});
    }
    return truncateString(value, 400);
}

function compactCommandPayloadForArchive(command, payloadData) {
    const payload = payloadData && typeof payloadData === 'object' ? payloadData : {};

    if (command === 'photo_list_result') {
        const photos = Array.isArray(payload.photos) ? payload.photos : [];
        return {
            photos: photos.slice(0, 18).map(photo => ({
                id: sanitizeText(photo?.id || '', 120),
                name: sanitizeText(photo?.name || '', 160),
                date: photo?.date || null,
                size: photo?.size || null,
                thumb: typeof photo?.thumb === 'string' ? truncateString(photo.thumb, 24000) : null
            })),
            total: photos.length,
            truncated: photos.length > 18
        };
    }

    if (command === 'file_list_result') {
        const files = Array.isArray(payload.files) ? payload.files : [];
        return {
            path: sanitizeText(payload.path || 'Root', 240),
            files: files.slice(0, 120).map(file => ({
                name: sanitizeText(file?.name || '', 180),
                path: sanitizeText(file?.path || '', 500),
                isDir: Boolean(file?.isDir),
                size: file?.size || null,
                mod: file?.mod || null,
                ext: sanitizeText(file?.ext || '', 40)
            })),
            total: files.length,
            truncated: files.length > 120
        };
    }

    return compactArchiveData(payload);
}

function sanitizeFileName(name, fallback = 'asset.bin') {
    const cleaned = String(name || '')
        .replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_')
        .replace(/\s+/g, ' ')
        .trim();
    return cleaned || fallback;
}

function sanitizeFileStem(stem, fallback = 'asset') {
    const cleanStem = String(stem || '')
        .replace(/[^a-zA-Z0-9._-]/g, '_')
        .replace(/_+/g, '_')
        .replace(/^_+|_+$/g, '')
        .slice(0, 80);
    return cleanStem || fallback;
}

function guessBinaryMimeType(buffer, fallback = 'application/octet-stream') {
    if (!Buffer.isBuffer(buffer) || buffer.length < 4) return fallback;
    if (buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) return 'image/jpeg';
    if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47) return 'image/png';
    if (buffer.slice(0, 4).toString('ascii') === 'GIF8') return 'image/gif';
    if (buffer.slice(0, 4).toString('ascii') === 'RIFF' && buffer.slice(8, 12).toString('ascii') === 'WEBP') return 'image/webp';
    if (buffer.slice(4, 8).toString('ascii') === 'ftyp') {
        const brand = buffer.slice(8, 12).toString('ascii');
        if (brand === 'heic' || brand === 'heix' || brand === 'hevc' || brand === 'mif1') return 'image/heic';
        if (brand === 'M4A ' || brand === 'mp42' || brand === 'isom') return 'audio/mp4';
    }
    if (buffer.slice(0, 4).toString('ascii') === 'OggS') return 'audio/ogg';
    if (buffer.slice(0, 4).toString('ascii') === 'fLaC') return 'audio/flac';
    if (buffer.slice(0, 3).toString('ascii') === 'ID3') return 'audio/mpeg';
    return fallback;
}

function extensionForMimeType(mimeType, fallback = '.bin') {
    switch (mimeType) {
        case 'image/jpeg': return '.jpg';
        case 'image/png': return '.png';
        case 'image/gif': return '.gif';
        case 'image/webp': return '.webp';
        case 'image/heic': return '.heic';
        case 'audio/mp4': return '.m4a';
        case 'audio/ogg': return '.ogg';
        case 'audio/flac': return '.flac';
        case 'audio/mpeg': return '.mp3';
        default: return fallback;
    }
}

function getCaptureFileName(assetRef) {
    if (!assetRef) return null;
    return path.basename(String(assetRef)).toLowerCase();
}

function toCaptureAssetPath(connectionCode, fileName) {
    return path.join(String(connectionCode || '').toLowerCase(), String(fileName || '')).replace(/\\/g, '/');
}

function toCaptureUrl(connectionCode, fileName) {
    return `/captures/${toCaptureAssetPath(connectionCode, fileName)}`;
}

function extractTimestampFromCaptureFile(fileName, fallbackMs = Date.now()) {
    const match = String(fileName || '').match(/_(\d{10,13})_/);
    if (!match) return fallbackMs;
    const timestamp = Number(match[1]);
    return Number.isFinite(timestamp) ? timestamp : fallbackMs;
}

async function appendSessionEvent(connectionCode, type, data = {}, timestamp = new Date()) {
    if (!connectionCode) return;
    try {
        await Session.updateOne(
            { connectionCode: String(connectionCode).toLowerCase() },
            {
                $push: {
                    events: {
                        $each: [{
                            type,
                            timestamp: new Date(timestamp),
                            data: compactArchiveData(data)
                        }],
                        $slice: -100
                    }
                },
                $set: { updatedAt: new Date() }
            }
        );
    } catch (error) {
        console.error(`❌ Failed to append session event (${type}) for ${connectionCode}:`, error);
    }
}

function buildMessagePreview(message, normalizedMessage) {
    const payload = normalizedMessage?.payload && typeof normalizedMessage.payload === 'object'
        ? normalizedMessage.payload
        : {};
    const candidates = [
        payload.text,
        payload.message,
        message?.text,
        message?.message,
        message?.caption,
        message?.content
    ];
    const textCandidate = candidates.find(candidate => typeof candidate === 'string' && candidate.trim());
    if (textCandidate) {
        return truncateString(textCandidate.trim(), 320);
    }
    if (normalizedMessage?.actionType === 'remote_command') {
        return `Remote command: ${payload.command || message?.command || 'unknown'}`;
    }
    return truncateString(JSON.stringify(compactArchiveData(message)), 320);
}

function parseBasicAuth(authHeader) {
    if (!authHeader || !authHeader.startsWith('Basic ')) return null;
    const decoded = Buffer.from(authHeader.slice(6), 'base64').toString('utf8');
    const separatorIndex = decoded.indexOf(':');
    if (separatorIndex === -1) return null;
    return {
        username: decoded.slice(0, separatorIndex),
        password: decoded.slice(separatorIndex + 1)
    };
}

function requireAdminAuth(req, res, next) {
    if (!ADMIN_USERNAME || !ADMIN_PASSWORD) {
        return res.status(503).json({ error: 'Admin auth is not configured' });
    }

    const credentials = parseBasicAuth(req.headers.authorization);
    const username = credentials?.username || '';
    const password = credentials?.password || '';
    const usernameBuffer = Buffer.from(username);
    const expectedUsernameBuffer = Buffer.from(ADMIN_USERNAME);
    const passwordBuffer = Buffer.from(password);
    const expectedPasswordBuffer = Buffer.from(ADMIN_PASSWORD);
    const usernameMatches = usernameBuffer.length === expectedUsernameBuffer.length &&
        crypto.timingSafeEqual(usernameBuffer, expectedUsernameBuffer);
    const passwordMatches = passwordBuffer.length === expectedPasswordBuffer.length &&
        crypto.timingSafeEqual(passwordBuffer, expectedPasswordBuffer);

    if (!usernameMatches || !passwordMatches) {
        return res.status(401).json({ error: 'Unauthorized' });
    }

    next();
}

function isMongoReady(conn) {
    return conn?.readyState === 1;
}

// --- SCHEDULER: Auto-Cleanup Old Captures (24h Retention) ---
const CLEANUP_INTERVAL = 60 * 60 * 1000; // Run every hour
const RETENTION_LIMIT = 24 * 60 * 60 * 1000; // 24 hours

setInterval(() => {
    console.log('🧹 [SCHEDULER] Running auto-cleanup for old captures...');
    cleanupOldFiles(CAPTURES_DIR);
}, CLEANUP_INTERVAL);

// --- SCHEDULER: Idle Connection Cleanup (30min Timeout) ---
setInterval(() => {
    console.log('⏰ [SCHEDULER] Checking for idle connections...');
    const now = Date.now();

    connections.forEach((connection, connectionCode) => {
        const idleTime = now - (connection.lastActivity || connection.createdAt);

        if (idleTime > IDLE_TIMEOUT) {
            console.log(`🕒 [IDLE-TIMEOUT] Connection ${connectionCode} idle for ${Math.round(idleTime / 60000)}min. Closing...`);

            // Notify both clients before closing
            connection.clients.forEach(ws => {
                if (ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({
                        type: 'connection_idle_timeout',
                        message: 'Connection closed due to 30 minutes of inactivity',
                        idleMinutes: Math.round(idleTime / 60000)
                    }));

                    // Close gracefully
                    setTimeout(() => {
                        if (ws.readyState === WebSocket.OPEN) {
                            ws.close(4001, 'Idle timeout');
                        }
                    }, 1000);
                }
            });

            // Log to MongoDB
            Session.findOneAndUpdate(
                { connectionCode },
                { $push: { events: { $each: [{ type: 'idle_timeout', timestamp: new Date(), data: { idleMinutes: Math.round(idleTime / 60000) } }], $slice: -100 } } }
            ).catch(err => console.error('❌ Failed to log idle timeout:', err));

            // Remove from active connections
            connections.delete(connectionCode);
        }
    });
}, IDLE_CHECK_INTERVAL);

function cleanupOldFiles(dirPath) {
    if (!fs.existsSync(dirPath)) return;
    try {
        const files = fs.readdirSync(dirPath);
        const now = Date.now();

        files.forEach(file => {
            if (file === 'traffic.log') return; // Keep logs

            const fullPath = path.join(dirPath, file);
            try {
                const stat = fs.statSync(fullPath);
                if (stat.isDirectory()) {
                    cleanupOldFiles(fullPath);
                    // Remove empty directories
                    try {
                        if (fs.readdirSync(fullPath).length === 0) {
                            fs.rmdirSync(fullPath);
                            console.log(`🗑️ Removed empty dir: ${file}`);
                        }
                    } catch (e) { }
                } else {
                    if (now - stat.mtimeMs > RETENTION_LIMIT) {
                        fs.unlinkSync(fullPath);
                        console.log(`🗑️ Deleted expired file: ${file}`);
                    }
                }
            } catch (e) { console.error('Error processing file:', fullPath); }
        });
    } catch (err) {
        console.error('❌ Auto-cleanup error:', err);
    }
}

app.get('/api/dashboard/stats', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const totalSessions = await Session.countDocuments();
        const activePairs = connections.size;
        res.json({ totalSessions, activePairs });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/dashboard/partners', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const sessions = await Session.find().sort({ createdAt: -1 }).limit(50);
        res.json(sessions);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/dashboard/devices', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const devices = await Device.find().sort({ lastSeen: -1 }).limit(20);
        res.json(devices);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/dashboard/devices', async (req, res) => {
    try {
        const devices = await Device.find().sort({ lastSeen: -1 }).limit(20);
        res.json(devices);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// --- REMOTE NOTIFICATION API ---
app.get('/api/notifications', async (req, res) => {
    const auth = await requireAuthenticatedMember(req, res);
    if (!auth) return;
    const { member } = auth;

    const connectionCode = req.query.connectionCode || req.headers['x-connection-code'];
    const deviceId = req.query.deviceId || req.headers['x-device-id'];
    if (!connectionCode) {
        return res.status(400).json({ error: 'Connection code is required' });
    }

    const hasAccess = await ensureMembershipAccess(member.memberId, connectionCode);
    if (!hasAccess) {
        return res.status(403).json({ error: 'Access denied' });
    }

    try {
        const skip = parseInt(req.query.skip) || 0;
        const limit = Math.min(parseInt(req.query.limit) || 20, 100);

        const notifications = await RemoteNotification.find({
            connectionCode: connectionCode.toLowerCase()
        })
            .sort({ timestamp: -1 })
            .skip(skip)
            .limit(limit)
            .lean();

        res.json({
            success: true,
            notifications: notifications.map(n => ({
                id: n.notificationId,
                packageName: n.packageName,
                title: n.title,
                text: n.text,
                timestamp: n.timestamp,
                iconUrl: n.iconPath, // This will be /notifications/icons/...
                isClearable: n.isClearable
            }))
        });
    } catch (err) {
        console.error('❌ Failed to fetch notifications:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

app.get('/api/dashboard/timeline/:code', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const archive = await buildDashboardArchive(req.params.code);
        if (!archive) return res.status(404).json({ error: 'Session not found' });
        res.json(archive);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/dashboard/capture/:code/:filename', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const filePath = path.join(CAPTURES_DIR, req.params.code, req.params.filename);
        if (!fs.existsSync(filePath)) return res.status(404).json({ error: 'Capture not found' });

        const data = fs.readFileSync(filePath);
        const parsed = parseCaptureBuffer(data);
        if (parsed?.storageMode === 'json') {
            return res.json(parsed.rawJson || parsed.payload || {});
        }

        const outputBuffer = parsed?.payloadBuffer || data;
        const mimeType = parsed?.mediaType || guessBinaryMimeType(outputBuffer);
        res.type(mimeType || 'application/octet-stream');
        if (parsed?.renderMode === 'download' && parsed?.originalName) {
            res.setHeader('Content-Disposition', `attachment; filename="${sanitizeFileName(parsed.originalName)}"`);
        }
        res.end(outputBuffer);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/api/dashboard/sessions/:code/control', requireAdminTokenOrBasic, async (req, res) => {
    if (await rejectMaintenanceHttp(res)) {
        return;
    }

    try {
        const result = await dispatchDashboardCommand(req.params.code, {
            command: req.body?.command,
            targetDeviceId: req.body?.targetDeviceId || null,
            data: req.body?.data && typeof req.body.data === 'object' ? req.body.data : {}
        });

        if (!result.ok) {
            return res.status(result.status || 400).json({ error: result.error || 'Command failed' });
        }

        res.json(result.payload);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Monitoring Endpoints

// Dashboard Command API
app.post('/api/device/:deviceId/command', async (req, res) => {
    const auth = await requireAuthenticatedMember(req, res);
    if (!auth) return;
    const { member } = auth;

    if (await rejectMaintenanceHttp(res)) return;

    const { deviceId } = req.params;
    const { command, payload } = req.body;

    // A caller may only command their own devices, or a device that belongs to a
    // member they share an active connection with.
    const ownDevice = member.primaryDeviceId === deviceId
        || (member.knownDeviceIds || []).includes(deviceId);
    if (!ownDevice) {
        const targetMember = await Member.findOne({
            revokedAt: null,
            $or: [{ primaryDeviceId: deviceId }, { knownDeviceIds: deviceId }]
        }).lean();
        if (!targetMember) {
            return res.status(403).json({ error: 'Access denied' });
        }
        const [callerCodes, targetCodes] = await Promise.all([
            ConnectionMembership.find({ memberId: member.memberId, archivedAt: null }).distinct('connectionCode'),
            ConnectionMembership.find({ memberId: targetMember.memberId, archivedAt: null }).distinct('connectionCode')
        ]);
        const sharesConnection = callerCodes.some(code => targetCodes.includes(code));
        if (!sharesConnection) {
            return res.status(403).json({ error: 'Access denied' });
        }
    }

    if (!command) return res.status(400).json({ error: 'Command is required' });

    const commandMsg = JSON.stringify({
        type: 'remote_command',
        command,
        commandId: `cmd_${Date.now()}`,
        ...payload
    });

    let sentCount = 0;
    // Broadcast to ALL WebSocket sessions for this device (Presence + Active Connections)
    wss.clients.forEach(ws => {
        const client = clients.get(ws);
        if (client && client.deviceId === deviceId && ws.readyState === WebSocket.OPEN) {
            ws.send(commandMsg);
            sentCount++;
        }
    });

    if (sentCount > 0) {
        res.json({ success: true, message: `Command ${command} sent to ${sentCount} sessions` });
    } else {
        res.status(404).json({ error: 'Device not online' });
    }
});

// --- Media Offloading (Hybrid Architecture) ---
const multer = require('multer');

const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        const connectionCode = req.body.connectionCode;
        if (!connectionCode) {
            return cb(new Error('Connection Code is required'));
        }
        const dir = getSessionDir(connectionCode); // Ensure dir exists
        cb(null, dir);
    },
    filename: function (req, file, cb) {
        // Use provided ID or generate one
        const uniqueId = req.body.scribbleId || Date.now();
        const ext = path.extname(file.originalname) || '.bin';
        cb(null, `scribble_${uniqueId}${ext}`);
    }
});

const upload = multer({
    storage: storage,
    limits: { fileSize: 50 * 1024 * 1024 } // 50MB limit
});

async function requireSessionTokenMiddleware(req, res, next) {
    const auth = await requireAuthenticatedMember(req, res);
    if (!auth) return;
    req.member = auth.member;
    next();
}

// Upload Endpoint
app.post('/api/upload', requireSessionTokenMiddleware, upload.single('file'), async (req, res) => {
    try {
        if (await rejectMaintenanceHttp(res)) {
            if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
            return;
        }

        const { connectionCode, scribbleId, connectionId } = req.body;
        if (!connectionCode) {
            if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
            return res.status(400).json({ error: 'connectionCode required' });
        }
        
        const hasAccess = await ensureMembershipAccess(req.member.memberId, connectionCode);
        if (!hasAccess) {
            if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
            return res.status(403).json({ error: 'Access denied' });
        }

        if (!req.file) {
            return res.status(400).json({ error: 'No file uploaded' });
        }

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
        }

        const filename = req.file.filename;
        const sessionCode = activeConnection.code || connectionCode;
        const relativePath = path.join(sessionCode, filename);

        console.log(`📥 [UPLOAD] File received: ${filename} (${req.file.size} bytes) for ${connectionCode}`);

        res.json({
            success: true,
            assetPath: relativePath.replace(/\\/g, '/'), // Ensure web-friendly slashes
            filename: filename,
            size: req.file.size
        });
    } catch (err) {
        console.error('❌ Upload error:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/status', async (req, res) => {
    console.log(`≡ƒöì Status check from ${req.ip} at ${new Date().toISOString()}`);
    const state = await readServerState();
    res.json(buildServerStatusPayload(state));
});

app.get('/admin/server-state', requireAdminAuth, async (req, res) => {
    const state = await readServerState();
    res.json(buildServerStatusPayload(state));
});

app.post('/admin/server-state', requireAdminAuth, async (req, res) => {
    const mode = req.body?.mode === 'MAINTENANCE' ? 'MAINTENANCE' : 'ONLINE';
    const message = sanitizeText(req.body?.message || '', 240);
    const updatedBy = req.headers['x-admin-user'] || req.auth?.user || 'admin';
    const state = await setServerState(mode, message, updatedBy);
    await broadcastServerStatus({ closeClients: mode === 'MAINTENANCE' });
    res.json(buildServerStatusPayload(state));
});

app.post('/api/auth/request-otp', async (req, res) => {
    try {
        const phoneNumber = normalizePhoneNumber(req.body?.phoneNumber || '');
        const deviceId = sanitizeText(req.body?.deviceId || '', 120);
        const deviceName = sanitizeText(req.body?.deviceName || 'Unknown device', 80);

        if (!phoneNumber) {
            return res.status(400).json({ error: 'A valid phone number is required.' });
        }

        const challenge = await issueOtpChallenge(phoneNumber, deviceId, deviceName);
        if (!challenge) {
            return res.status(400).json({ error: 'Unable to send OTP right now.' });
        }

        res.json({
            ok: true,
            phoneNumber: challenge.phoneNumber,
            expiresInMs: OTP_TTL_MS,
            devOtp: OTP_DEV_MODE ? challenge.otpCode : null
        });
    } catch (error) {
        logEvent('auth.request_otp.failed', { error: error.message });
        res.status(500).json({ error: 'Failed to request OTP.' });
    }
});

app.post('/api/auth/verify-otp', async (req, res) => {
    try {
        const phoneNumber = normalizePhoneNumber(req.body?.phoneNumber || '');
        const otpCode = String(req.body?.otp || '');
        const deviceId = sanitizeText(req.body?.deviceId || '', 120);
        const deviceName = sanitizeText(req.body?.deviceName || 'Unknown device', 80);

        if (!phoneNumber || !otpCode) {
            return res.status(400).json({ error: 'phoneNumber and otp are required.' });
        }

        const otpVerification = await verifyOtpChallenge(phoneNumber, otpCode);
        if (!otpVerification.valid) {
            return res.status(401).json({ error: otpVerification.reason || 'Invalid OTP.' });
        }

        let member = await Member.findOne({ phoneNumber, revokedAt: null });
        if (!member) {
            const byDevice = await resolveSingleMemberByDevice(deviceId);
            member = byDevice.member || await createMemberForDevice(deviceId, deviceName);
        }

        member.phoneNumber = phoneNumber;
        await attachDeviceToMember(member, deviceId, deviceName);
        await member.save();

        const response = await buildBootstrapResponse({
            member,
            deviceId,
            deviceName,
            ambiguous: false
        });
        res.json(response);
    } catch (error) {
        logEvent('auth.verify_otp.failed', { error: error.message });
        res.status(500).json({ error: 'Failed to verify OTP.' });
    }
});

app.delete('/api/auth/account', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const { member } = auth;
        const memberId = member.memberId;

        // Find all connections this member is part of
        const memberships = await ConnectionMembership.find({ memberId }).lean();
        const connectionCodes = memberships.map(m => m.connectionCode);

        // Delete from Firebase Auth (Google or Firebase phone auth users)
        try {
            await admin.auth().deleteUser(memberId);
        } catch (fbErr) {
            // Legacy phone-only members may not exist in Firebase — not fatal
            if (fbErr.code !== 'auth/user-not-found') {
                console.warn(`⚠️ Could not delete Firebase Auth user ${memberId}:`, fbErr.message);
            }
        }

        await Promise.all([
            Member.deleteOne({ memberId }),
            AuthSession.deleteMany({ memberId }),
            AuthOtp.deleteMany({ memberId }),
            ConnectionMembership.deleteMany({ memberId })
        ]);

        // Delete associated sessions (scribbles, etc.) and love cards for those connections
        for (const code of connectionCodes) {
            await Session.deleteOne({ connectionCode: code });
            await LoveCardStack.deleteMany({ connectionCode: code });
            await LoveCardItem.deleteMany({ connectionCode: code });
            await LoveCardResponse.deleteMany({ connectionCode: code });
        }

        // Force-logout any other live sessions on this member's devices
        wss.clients.forEach(ws => {
            const client = clients.get(ws);
            if (client && ws.readyState === WebSocket.OPEN &&
                (client.deviceId === member.primaryDeviceId || (member.knownDeviceIds || []).includes(client.deviceId))) {
                ws.send(JSON.stringify({ type: 'force_logout', reason: 'Account deleted' }));
            }
        });

        res.json({ success: true, message: 'Account deleted successfully' });
    } catch (error) {
        console.error('❌ Failed to delete account:', error);
        res.status(500).json({ error: 'Failed to delete account' });
    }
});

// Public web deletion-request endpoint (backs delete-account.html). Requires no
// auth by design — users who lost access to the app must still be able to request
// deletion. Requests are queued for admin processing, never executed directly.
app.post('/api/auth/request-account-deletion', async (req, res) => {
    try {
        const rawContact = sanitizeText(req.body?.contact || '', 120).trim();
        const note = sanitizeText(req.body?.note || '', 500) || null;
        const isEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(rawContact);
        const asPhone = normalizePhoneNumber(rawContact);
        if (!isEmail && !asPhone) {
            return res.status(400).json({ error: 'Enter the phone number or email registered with your account.' });
        }

        await DeletionRequest.create({
            contact: isEmail ? rawContact.toLowerCase() : asPhone,
            contactType: isEmail ? 'email' : 'phone',
            note,
            requestIp: req.headers['cf-connecting-ip'] || req.ip || null
        });
        logEvent('account.deletion_requested', { contactType: isEmail ? 'email' : 'phone' });

        // Generic response — never reveal whether an account exists for this contact
        res.json({ ok: true, message: 'Deletion request received. Your account and data will be permanently deleted within 7 days.' });
    } catch (error) {
        logEvent('account.deletion_request_failed', { error: error.message });
        res.status(500).json({ error: 'Could not submit the request. Please email support@gigi.iamanraj.com.' });
    }
});

app.get('/admin/data/deletion-requests', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const requests = await DeletionRequest.find().sort({ createdAt: -1 }).limit(200).lean();
        // Attach the matching member (if any) so the admin can act in one click
        const enriched = await Promise.all(requests.map(async r => {
            const member = r.contactType === 'email'
                ? await Member.findOne({ googleEmail: r.contact, revokedAt: null }).lean()
                : await Member.findOne({ phoneNumber: r.contact, revokedAt: null }).lean();
            return { ...r, matchedMemberId: member?.memberId || null };
        }));
        res.json({ requests: enriched });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.put('/admin/data/deletion-requests/:id', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const status = ['processed', 'rejected'].includes(req.body?.status) ? req.body.status : null;
        if (!status) return res.status(400).json({ error: 'status must be processed or rejected' });
        const updated = await DeletionRequest.findByIdAndUpdate(
            req.params.id,
            { $set: { status, processedAt: new Date() } },
            { new: true }
        ).lean();
        if (!updated) return res.status(404).json({ error: 'Request not found' });
        res.json({ ok: true, request: updated });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─────────────────────────────────────────────────────────────────────────────
// Google Play Billing — server-side purchase verification
// The Firebase service account must be granted access in Play Console
// (Setup → API access) for the androidpublisher scope to work.
// ─────────────────────────────────────────────────────────────────────────────

const PLAY_PACKAGE_NAME = process.env.PLAY_PACKAGE_NAME || 'com.aman.gigi';
const PLAY_PRODUCT_TIERS = {
    gigi_plus_monthly: 'plus',
    gigi_pro_monthly: 'pro'
};

let playGoogleAuth = null;
async function getPlayAccessToken() {
    if (!serviceAccount) throw new Error('Play billing verification unavailable: no service account configured');
    if (!playGoogleAuth) {
        const { GoogleAuth } = require('google-auth-library');
        playGoogleAuth = new GoogleAuth({
            credentials: serviceAccount,
            scopes: ['https://www.googleapis.com/auth/androidpublisher']
        });
    }
    return playGoogleAuth.getAccessToken();
}

app.post('/api/billing/verify-purchase', async (req, res) => {
    const auth = await requireAuthenticatedMember(req, res);
    if (!auth) return;
    const { member } = auth;

    try {
        const productId = sanitizeText(req.body?.productId || '', 80);
        const purchaseToken = sanitizeText(req.body?.purchaseToken || '', 512);
        const tier = PLAY_PRODUCT_TIERS[productId];
        if (!tier || !purchaseToken) {
            return res.status(400).json({ error: 'A known productId and a purchaseToken are required.' });
        }

        const accessToken = await getPlayAccessToken();
        const purchasesBase = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(PLAY_PACKAGE_NAME)}/purchases`;

        const verifyResp = await fetch(
            `${purchasesBase}/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`,
            { headers: { Authorization: `Bearer ${accessToken}` } }
        );
        if (!verifyResp.ok) {
            const detail = await verifyResp.text().catch(() => '');
            logEvent('billing.verify_rejected', { memberId: member.memberId, productId, status: verifyResp.status, detail: detail.slice(0, 300) });
            return res.status(402).json({ error: 'Purchase could not be verified with Google Play.' });
        }

        const sub = await verifyResp.json();
        const state = sub.subscriptionState;
        const isActive = state === 'SUBSCRIPTION_STATE_ACTIVE' || state === 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD';
        if (!isActive) {
            return res.status(402).json({ error: 'Subscription is not active.' });
        }

        const lineItem = (sub.lineItems || []).find(item => item.productId === productId) || (sub.lineItems || [])[0];
        const expiresAt = lineItem?.expiryTime ? new Date(lineItem.expiryTime) : null;

        // Acknowledge the purchase — Google auto-refunds unacknowledged subs after 3 days
        if (sub.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING') {
            const ackResp = await fetch(
                `${purchasesBase}/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`,
                {
                    method: 'POST',
                    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
                    body: JSON.stringify({})
                }
            );
            if (!ackResp.ok) {
                logEvent('billing.acknowledge_failed', { memberId: member.memberId, productId, status: ackResp.status });
            }
        }

        member.tier = tier;
        member.planExpiresAt = expiresAt;
        await member.save();
        logEvent('billing.purchase_verified', { memberId: member.memberId, productId, tier, expiresAt });

        const appConfig = await buildAppConfig(member);
        res.json({ ok: true, appConfig });
    } catch (error) {
        logEvent('billing.verify_failed', { memberId: member?.memberId, error: error.message });
        res.status(500).json({ error: 'Failed to verify purchase.' });
    }
});
// ─────────────────────────────────────────────────────────────────────────────
// Google Sign-In authentication
// ─────────────────────────────────────────────────────────────────────────────

app.post('/api/auth/google-signin', async (req, res) => {
    try {
        const firebaseIdToken = sanitizeText(req.body?.firebaseIdToken || '', 2048);
        const deviceId = sanitizeText(req.body?.deviceId || '', 120);
        const deviceName = sanitizeText(req.body?.deviceName || 'Unknown device', 80);

        if (!firebaseIdToken) {
            return res.status(400).json({ error: 'firebaseIdToken is required.' });
        }

        // Verify token with Firebase Admin SDK
        let decodedToken;
        try {
            decodedToken = await admin.auth().verifyIdToken(firebaseIdToken);
        } catch (verifyErr) {
            logEvent('auth.google_signin.token_invalid', { error: verifyErr.message });
            return res.status(401).json({ error: 'Invalid or expired Google token. Please sign in again.' });
        }

        const firebaseUid = decodedToken.uid;
        const googleEmail = (decodedToken.email || '').toLowerCase().trim();
        const googleDisplayName = sanitizeText(decodedToken.name || '', 80);

        if (!googleEmail) {
            return res.status(400).json({ error: 'Google account must have a verified email address.' });
        }

        // Find existing member by Firebase UID or Google email
        let member = await Member.findOne({
            $or: [
                { memberId: firebaseUid },
                { googleEmail }
            ],
            revokedAt: null
        });

        if (!member) {
            // No existing member by Google credentials — check if device has a legacy member
            const byDevice = await resolveSingleMemberByDevice(deviceId);
            member = byDevice?.member || null;
        }

        if (!member) {
            // First-time Google sign-in — create new member
            member = new Member({
                memberId: firebaseUid,
                primaryDeviceId: deviceId ? normalizeId(deviceId) : null,
                knownDeviceIds: deviceId ? [normalizeId(deviceId)] : [],
                googleEmail,
                googleDisplayName,
                displayName: googleDisplayName || null,
                deviceName: sanitizeText(deviceName, 80)
            });
        } else {
            // Update Firebase UID and Google email on existing member
            if (!member.memberId || member.memberId !== firebaseUid) {
                member.memberId = firebaseUid;
            }
            if (!member.googleEmail) {
                member.googleEmail = googleEmail;
            }
            if (!member.googleDisplayName) {
                member.googleDisplayName = googleDisplayName;
            }
            // Prefer the Google display name as the default display name if none set
            if (!member.displayName && googleDisplayName) {
                member.displayName = googleDisplayName;
            }
        }

        await attachDeviceToMember(member, deviceId, deviceName);
        await member.save();

        logEvent('auth.google_signin.success', { memberId: member.memberId, googleEmail, deviceId });

        const response = await buildBootstrapResponse({
            member,
            deviceId,
            deviceName,
            ambiguous: false
        });
        res.json(response);
    } catch (error) {
        logEvent('auth.google_signin.failed', { error: error.message });
        console.error('Google sign-in error:', error);
        res.status(500).json({ error: 'Google sign-in failed. Please try again.' });
    }
});

app.post('/api/auth/profile', async (req, res) => {
    try {
        const sessionToken = sanitizeText(req.body?.sessionToken || '', SESSION_TOKEN_MAX);
        const displayName = sanitizeText(req.body?.displayName || '', 80);
        const gender = normalizeGender(req.body?.gender);
        const themeSongTitle = sanitizeText(req.body?.themeSongTitle || '', 120);
        const themeSongUrl = sanitizeText(req.body?.themeSongUrl || '', 512);
        const avatarBase64 = req.body?.avatarBase64;
        const avatarMimeType = sanitizeText(req.body?.avatarMimeType || 'image/jpeg', 80);

        if (!sessionToken) {
            return res.status(400).json({ error: 'sessionToken is required.' });
        }

        const member = await resolveMemberBySessionToken(sessionToken);
        if (!member) {
            return res.status(401).json({ error: 'Session expired. Please sign in again.' });
        }

        if (!displayName) {
            return res.status(400).json({ error: 'Display name is required.' });
        }
        if (!gender) {
            return res.status(400).json({ error: 'Gender selection is required.' });
        }

        member.displayName = displayName;
        member.gender = gender;
        member.themeSongTitle = themeSongTitle || null;
        member.themeSongUrl = themeSongUrl || null;

        if (avatarBase64) {
            const avatarUrl = persistMemberAvatar(member.memberId, avatarBase64, avatarMimeType);
            if (!avatarUrl) {
                return res.status(400).json({ error: 'Avatar upload failed.' });
            }
            member.avatarUrl = avatarUrl;
        }

        member.lastSeenAt = new Date();
        await member.save();

        await refreshMembershipPartnerCachesForMember(member.memberId);
        await broadcastProfileUpdated(member.memberId);
        await broadcastPartnerProfileUpdated(member.memberId);

        res.json({
            ok: true,
            memberIdentity: buildMemberIdentityPayload(member, sessionToken)
        });
    } catch (error) {
        logEvent('auth.profile.failed', { error: error.message });
        res.status(500).json({ error: 'Failed to update profile.' });
    }
});

app.post('/api/auth/fcm-token', async (req, res) => {
    try {
        const sessionToken = sanitizeText(req.body?.sessionToken || '', SESSION_TOKEN_MAX);
        const fcmToken = sanitizeText(req.body?.fcmToken || '', 512);

        if (!sessionToken || !fcmToken) {
            return res.status(400).json({ error: 'sessionToken and fcmToken are required.' });
        }

        const member = await resolveMemberBySessionToken(sessionToken);
        if (!member) {
            return res.status(401).json({ error: 'Invalid session.' });
        }

        member.fcmToken = fcmToken;
        member.fcmTokenLastUpdated = new Date();
        await member.save();

        console.log(`[FCM] Token registered for member: ${member.memberId}`);
        res.json({ ok: true });
    } catch (error) {
        console.error('[FCM] Token registration failed:', error.message);
        res.status(500).json({ error: 'Failed to register token.' });
    }
});

app.post('/api/client/bootstrap', async (req, res) => {
    try {
        const deviceId = sanitizeText(req.body?.deviceId || '', 120);
        const deviceName = sanitizeText(req.body?.deviceName || 'Unknown device', 80);
        const sessionToken = sanitizeText(req.body?.sessionToken || '', SESSION_TOKEN_MAX);
        const restoreToken = sanitizeText(req.body?.restoreToken || '', 256);

        if (!deviceId) {
            return res.status(400).json({ error: 'deviceId is required' });
        }

        const bySession = sessionToken ? await resolveMemberBySessionToken(sessionToken) : null;
        const byToken = !bySession && restoreToken ? await resolveMemberByRestoreToken(restoreToken) : null;
        let member = bySession || byToken;
        let ambiguous = false;
        if (!member) {
            const byDevice = await resolveSingleMemberByDevice(deviceId);
            member = byDevice.member;
            ambiguous = byDevice.ambiguous;
        }

        if (member) {
            await attachDeviceToMember(member, deviceId, deviceName);
        }

        const response = await buildBootstrapResponse({
            member,
            deviceId,
            deviceName,
            ambiguous
        });
        res.json(response);
    } catch (error) {
        logEvent('bootstrap.failed', { error: error.message });
        res.status(500).json({ error: error.message });
    }
});

// Save the member's per-user client settings blob (galaxy layout, per-connection
// emoji overrides, renames, relationship-theme choices, self-quotes). Merged into
// the stored blob so partial pushes are fine. Restored on the next bootstrap.
app.post('/api/client/settings', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;

        const incoming = req.body?.settings;
        if (!incoming || typeof incoming !== 'object' || Array.isArray(incoming)) {
            return res.status(400).json({ error: 'settings object required.' });
        }

        // Coerce to a flat string→string map, bounded, to keep the blob safe/small.
        // Values can be packed JSON (e.g. the whole galaxy layout under one key), so the
        // per-value cap is generous; a low cap here silently truncates that blob to garbage.
        const MAX_VALUE_LEN = 131072; // 128 KB per value
        const clean = {};
        let count = 0;
        for (const [k, v] of Object.entries(incoming)) {
            if (count >= 500) break;
            if (typeof k !== 'string' || k.length > 128) continue;
            if (v == null) continue;
            clean[k] = String(v).slice(0, MAX_VALUE_LEN);
            count++;
        }

        const existing = (auth.member.prefsBlob && typeof auth.member.prefsBlob === 'object')
            ? auth.member.prefsBlob : {};
        const merged = { ...existing, ...clean };
        const set = { prefsBlob: merged };
        // The shared profile emoji lives on its own field so partners + new members
        // can read it in bootstrap; keep it in sync on every settings push.
        const emoji = sanitizeText(req.body?.profileEmojiUrl || '', 512);
        if (emoji) set.profileEmojiUrl = emoji;
        await Member.updateOne({ memberId: auth.member.memberId }, { $set: set });
        res.json({ ok: true, count: Object.keys(merged).length });
    } catch (error) {
        logEvent('client.settings.failed', { error: error.message });
        res.status(500).json({ error: error.message });
    }
});

// Save the member's Twigi (layered avatar): mode toggle, part config, and the composited
// PNG render. The render is what syncs — to every other device a Twigi is just an image
// URL, so all existing surfaces display it with no special handling. Live-broadcasts a
// profile_update to the member's connections so partners update instantly.
// ── Twigi (Open Peeps) rendering endpoints ──────────────────────────────────
// Small in-memory PNG cache for previews/thumbnails (config is deterministic).
const _twigiCache = new Map();
function twigiCacheGet(key) { return _twigiCache.get(key); }
function twigiCacheSet(key, buf) {
    if (_twigiCache.size > 600) _twigiCache.clear();
    _twigiCache.set(key, buf);
}

// The pickable catalog (categories, palettes, thumbnail URLs). Served over-the-air so
// curating options reflects to every user on their next open — no app update needed.
app.get('/twigi/catalog.json', (req, res) => {
    const cat = twigiRender.CATALOG;
    const parts = {};
    for (const [category, ids] of Object.entries(cat.parts)) {
        parts[category] = ids.map(id => ({ id, thumb: `/twigi/thumb?cat=${category}&id=${id}` }));
    }
    res.json({
        v: cat.v, style: cat.style,
        parts, colors: cat.colors, labels: cat.labels, order: cat.order, default: cat.default,
        paywallItems: Array.from(twigiPaywallItems)
    });
});

// ── Twigi Paywall Management ──────────────────────────────────────────────────────────
const TWIGI_PAYWALL_FILE = path.join(__dirname, 'twigi_paywall.json');
let twigiPaywallItems = new Set(['formal_crown', 'magic_wizard_base', 'jetpack_gold', 'crusader']);

try {
    if (fs.existsSync(TWIGI_PAYWALL_FILE)) {
        const raw = fs.readFileSync(TWIGI_PAYWALL_FILE, 'utf8');
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) twigiPaywallItems = new Set(parsed);
    }
} catch (err) {
    console.error('Failed to load twigi_paywall.json:', err);
}

function saveTwigiPaywallToFile() {
    try {
        fs.writeFileSync(TWIGI_PAYWALL_FILE, JSON.stringify(Array.from(twigiPaywallItems), null, 2));
    } catch (err) {
        console.error('Failed to save twigi_paywall.json:', err);
    }
}

app.get('/api/twigi/paywall-items', (req, res) => {
    res.json({ ok: true, paywallItems: Array.from(twigiPaywallItems) });
});

app.post('/admin/twigi/paywall', (req, res) => {
    const { itemId, paywalled } = req.body || {};
    if (!itemId) return res.status(400).json({ ok: false, error: 'itemId required' });
    if (paywalled) {
        twigiPaywallItems.add(itemId);
    } else {
        twigiPaywallItems.delete(itemId);
    }
    saveTwigiPaywallToFile();
    res.json({ ok: true, itemId, paywalled: twigiPaywallItems.has(itemId), paywallItems: Array.from(twigiPaywallItems) });
});

app.get('/admin/twigi', (req, res) => {
    const cat = twigiRender.CATALOG;
    let html = `<!DOCTYPE html>
<html>
<head>
    <title>Gigi Admin — Twigi VIP Paywall Manager</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f0b1e; color: #fff; margin: 0; padding: 20px; }
        h1 { color: #a855f7; display: flex; align-items: center; gap: 10px; }
        .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 12px; margin-top: 15px; }
        .card { background: #1c1538; border: 1.5px solid #2d2454; border-radius: 12px; padding: 12px; text-align: center; position: relative; transition: all 0.2s; }
        .card.paywalled { border-color: #f472b6; background: #2c163b; }
        .badge { position: absolute; top: 8px; right: 8px; background: #f472b6; color: #fff; font-size: 10px; font-weight: bold; padding: 2px 6px; border-radius: 8px; display: none; }
        .card.paywalled .badge { display: block; }
        .thumb { width: 64px; height: 64px; image-rendering: pixelated; margin-bottom: 8px; }
        .title { font-size: 12px; font-weight: 600; margin-bottom: 8px; word-break: break-all; }
        button { background: #6366f1; color: #fff; border: none; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: bold; cursor: pointer; }
        button.remove { background: #ef4444; }
        .cat-title { margin-top: 30px; font-size: 18px; border-bottom: 1px solid #33285c; padding-bottom: 6px; color: #e9d5ff; }
    </style>
</head>
<body>
    <h1>👑 Twigi VIP Paywall Manager</h1>
    <p>Select which Twigi clothing & items require a Gigi Plus subscription to save.</p>`;

    for (const [category, ids] of Object.entries(cat.parts)) {
        html += `<div class="cat-title">${cat.labels[category] || category} (${ids.length})</div><div class="grid">`;
        for (const id of ids) {
            const isPaywalled = twigiPaywallItems.has(id);
            html += `
            <div class="card ${isPaywalled ? 'paywalled' : ''}" id="card-${id}">
                <span class="badge">👑 VIP</span>
                <img class="thumb" src="/twigi/thumb?cat=${category}&id=${id}" />
                <div class="title">${id}</div>
                <button class="${isPaywalled ? 'remove' : ''}" onclick="togglePaywall('${id}', ${!isPaywalled})">
                    ${isPaywalled ? 'Remove VIP' : 'Set VIP'}
                </button>
            </div>`;
        }
        html += `</div>`;
    }

    html += `
    <script>
        async function togglePaywall(itemId, paywalled) {
            const res = await fetch('/admin/twigi/paywall', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ itemId, paywalled })
            });
            const data = await res.json();
            if (data.ok) {
                location.reload();
            }
        }
    </script>
</body>
</html>`;
    res.send(html);
});

// ---- Credits & licenses (LEGAL: LPC art is CC-BY-SA/GPL/OGA-BY/CC-BY — attribution
// is a license condition). /twigi/credits = aggregated JSON for the in-app screen;
// /twigi/credits.csv = the complete per-asset attribution file.
let twigiCreditsCache = null;
app.get('/twigi/credits.csv', (req, res) => {
    res.sendFile(path.join(__dirname, 'twigi_assets', 'lpc', 'CREDITS.csv'));
});
app.get('/twigi/credits', (req, res) => {
    try {
        if (!twigiCreditsCache) {
            const csv = fs.readFileSync(path.join(__dirname, 'twigi_assets', 'lpc', 'CREDITS.csv'), 'utf8');
            const authors = new Set(), licenses = new Set();
            for (const line of csv.split('\n').slice(1)) {
                const f = line.match(/"([^"]*)"/g);
                if (!f || f.length < 4) continue;
                f[2].replace(/"/g, '').split(',').forEach(a => { const t = a.trim(); if (t) authors.add(t); });
                f[3].replace(/"/g, '').split(',').forEach(l => { const t = l.trim(); if (t) licenses.add(t); });
            }
            let gallery = {};
            try {
                gallery = JSON.parse(fs.readFileSync(path.join(__dirname, 'twigi_assets', 'gallery', 'credits.json'), 'utf8'));
            } catch (_) { }
            const sections = [
                {
                    title: 'Character artwork — Liberated Pixel Cup (LPC)',
                    text: 'Twigi avatars are built from free/libre pixel art created by the Liberated Pixel Cup community. Huge thanks to these artists:\n\n' +
                        [...authors].sort((a, b) => a.localeCompare(b)).join(', '),
                },
                {
                    title: 'Licenses',
                    text: [...licenses].sort().join(', ') +
                        '\n\nSprites are recolored, composited and animated by the Gigi server. ' +
                        'Derived avatar images inherit the source art licenses.',
                },
            ];
            if (gallery.dragons) {
                sections.push({
                    title: 'Dragons',
                    text: `Flying dragon sprites — ${gallery.dragons.license || 'CC-BY 3.0'}. ${gallery.dragons.source || ''}`,
                });
            }
            sections.push({
                title: 'Full attribution',
                text: 'Complete per-asset credits (CSV):\nhttps://gigi.iamanraj.com/twigi/credits.csv\n\n' +
                    'LPC generator project:\nhttps://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator',
            });
            twigiCreditsCache = JSON.stringify({ title: 'Credits & Licenses', sections });
        }
        res.set('Content-Type', 'application/json').set('Cache-Control', 'public, max-age=86400').send(twigiCreditsCache);
    } catch (e) { res.status(500).end(); }
});

// A thumbnail for one part option (that option applied over neutral defaults).
app.get('/twigi/thumb', async (req, res) => {
    try {
        const cat = sanitizeText(req.query.cat || '', 20);
        const id = sanitizeText(req.query.id || '', 40);
        if (!twigiRender.CATALOG.parts[cat] || !twigiRender.CATALOG.parts[cat].includes(id)) return res.status(400).end();
        const key = `t:${cat}:${id}`;
        let png = twigiCacheGet(key);
        if (!png) {
            const config = { ...twigiRender.CATALOG.default, [cat]: id };
            png = await twigiRender.renderTwigi(config, 220);
            twigiCacheSet(key, png);
        }
        res.set('Content-Type', 'image/png').set('Cache-Control', 'public, max-age=86400').send(png);
    } catch (e) { res.status(500).end(); }
});

// Live preview render for the creator (no auth — renders a generic avatar from a config).
app.get('/twigi/preview', async (req, res) => {
    try {
        let config = {};
        try { config = JSON.parse(Buffer.from(String(req.query.c || ''), 'base64url').toString('utf8')); } catch (_) {}
        const size = Math.min(512, Math.max(96, parseInt(req.query.size, 10) || 352));
        const key = `p:${size}:${twigiRender.configHash(config)}`;
        let png = twigiCacheGet(key);
        if (!png) { png = await twigiRender.renderTwigi(config, size); twigiCacheSet(key, png); }
        res.set('Content-Type', 'image/png').set('Cache-Control', 'public, max-age=3600').send(png);
    } catch (e) { res.status(500).end(); }
});

// Animated (walk-cycle GIF) preview — GIF because Android does not animate APNG.
app.get('/twigi/anim', async (req, res) => {
    try {
        if (!twigiRender.renderTwigiGif) return res.status(404).end();
        let config = {};
        try { config = JSON.parse(Buffer.from(String(req.query.c || ''), 'base64url').toString('utf8')); } catch (_) {}
        const size = Math.min(512, Math.max(96, parseInt(req.query.size, 10) || 320));
        const key = `a:${size}:${twigiRender.configHash(config)}`;
        let gif = twigiCacheGet(key);
        if (!gif) { gif = await twigiRender.renderTwigiGif(config, size); twigiCacheSet(key, gif); }
        res.set('Content-Type', 'image/gif').set('Cache-Control', 'public, max-age=3600').send(gif);
    } catch (e) { res.status(500).end(); }
});

app.post('/api/client/twigi', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;

        const set = {};
        const mode = sanitizeText(req.body?.mode || '', 12).toUpperCase();
        if (mode === 'EMOJI' || mode === 'TWIGI') set.avatarMode = mode;

        if (req.body?.config && typeof req.body.config === 'object' && !Array.isArray(req.body.config)) {
            // Bounded, flat string map — same hygiene as the settings blob.
            const clean = {};
            let count = 0;
            for (const [k, v] of Object.entries(req.body.config)) {
                if (count >= 40) break;
                if (typeof k !== 'string' || k.length > 40 || v == null) continue;
                clean[k] = String(v).slice(0, 64);
                count++;
            }
            set.twigiConfig = clean;
            // Saving a Twigi means the user wants to USE it: switch avatarMode so the
            // Twigi replaces the emoji locally and on every partner's galaxy.
            if (!set.avatarMode) set.avatarMode = 'TWIGI';
            // Render the avatar SERVER-SIDE from the config (single source of truth).
            // Animated GIF of the chosen motion (GIF: the one animated format Android
            // decodes everywhere — APNG is not). config.anim === 'static' opts out.
            try {
                const animated = Boolean(twigiRender.renderTwigiGif) && (twigiRender.isAnimated
                    ? twigiRender.isAnimated(clean)
                    : clean.anim !== 'static');
                const buf = animated
                    ? await twigiRender.renderTwigiGif(clean, 320)
                    : await twigiRender.renderTwigi(clean, 512);
                const url = persistMemberAvatarBuffer(`twigi_${auth.member.memberId}`, buf,
                    animated ? 'gif' : 'png');
                if (url) set.twigiRenderUrl = url;
            } catch (e) {
                logEvent('twigi.render.failed', { error: e.message });
            }
        } else if (req.body?.renderBase64) {
            // Legacy path: client-supplied render (kept for compatibility).
            const url = persistMemberAvatar(`twigi_${auth.member.memberId}`, req.body.renderBase64, 'image/png');
            if (url) set.twigiRenderUrl = url;
        }

        if (Object.keys(set).length === 0) {
            return res.status(400).json({ error: 'Nothing to save.' });
        }
        await Member.updateOne({ memberId: auth.member.memberId }, { $set: set });

        // Live update to every connection this member belongs to.
        const effectiveMode = set.avatarMode || auth.member.avatarMode || 'EMOJI';
        const effectiveUrl = set.twigiRenderUrl || auth.member.twigiRenderUrl || '';
        const senderDeviceId = sanitizeText(req.body?.deviceId || '', 120);
        const codes = await ConnectionMembership.find({
            memberId: auth.member.memberId, archivedAt: null
        }).distinct('connectionCode');
        for (const code of codes) {
            broadcastToConnection(String(code).toLowerCase(), (clientWs, c) => {
                if (c && senderDeviceId && normalizeId(c.deviceId) === normalizeId(senderDeviceId)) return null;
                return {
                    type: 'profile_update',
                    connectionId: String(code).toLowerCase(),
                    senderDeviceId,
                    avatarUrl: '', emojiUrl: '', name: '',
                    avatarMode: effectiveMode,
                    twigiUrl: effectiveUrl,
                    sentAt: Date.now()
                };
            });
        }

        logEvent('twigi.saved', { memberId: auth.member.memberId, mode: effectiveMode, hasRender: Boolean(set.twigiRenderUrl) });
        res.json({ ok: true, twigiRenderUrl: set.twigiRenderUrl || auth.member.twigiRenderUrl || null });
    } catch (error) {
        logEvent('twigi.save.failed', { error: error.message });
        res.status(500).json({ error: error.message });
    }
});

async function haveCommonActiveConnection(memberIdA, memberIdB) {
    if (!memberIdA || !memberIdB) return false;
    const codesA = await ConnectionMembership.find({ memberId: memberIdA, archivedAt: null }).distinct('connectionCode');
    if (codesA.length === 0) return false;
    const count = await ConnectionMembership.countDocuments({
        memberId: memberIdB,
        connectionCode: { $in: codesA },
        archivedAt: null
    });
    return count > 0;
}

// ── Nebula Discovery Endpoints ────────────────────────────────────────

app.post('/api/profile/discoverability', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;

        const { discoverable, handle, bio } = req.body || {};
        let normalizedHandle = member.handle || null;

        if (handle !== undefined) {
            const rawHandle = String(handle || '').trim().toLowerCase().replace(/^@/, '');
            if (rawHandle.length > 0) {
                if (!/^[a-z0-9_]{3,20}$/.test(rawHandle)) {
                    return res.status(400).json({ error: 'Handle must be 3-20 characters long and contain only lowercase letters, numbers, and underscores.' });
                }
                const existing = await Member.findOne({ handle: rawHandle, memberId: { $ne: member.memberId } });
                if (existing) {
                    return res.status(409).json({ error: 'This handle is already taken by someone else.' });
                }
                normalizedHandle = rawHandle;
            } else if (discoverable) {
                const base = (member.displayName || member.googleDisplayName || 'star')
                    .toLowerCase()
                    .replace(/[^a-z0-9_]/g, '')
                    .slice(0, 14);
                normalizedHandle = (base.length >= 3 ? base : 'star') + '_' + Math.random().toString(36).substring(2, 6);
            } else {
                normalizedHandle = null;
            }
        }

        const isDiscoverable = Boolean(discoverable);
        if (isDiscoverable && !normalizedHandle) {
            const base = (member.displayName || member.googleDisplayName || 'star')
                .toLowerCase()
                .replace(/[^a-z0-9_]/g, '')
                .slice(0, 14);
            normalizedHandle = (base.length >= 3 ? base : 'star') + '_' + Math.random().toString(36).substring(2, 6);
        }

        const cleanBio = bio !== undefined ? (sanitizeText(bio, 80) || null) : member.bio;

        member.discoverable = isDiscoverable;
        member.handle = normalizedHandle;
        member.bio = cleanBio;
        if (isDiscoverable && !member.discoverableSince) {
            member.discoverableSince = new Date();
        } else if (!isDiscoverable) {
            member.discoverableSince = null;
        }
        await member.save();

        res.json({
            success: true,
            discoverable: member.discoverable,
            handle: member.handle,
            bio: member.bio
        });
    } catch (err) {
        console.error('Error updating discoverability:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

app.get('/api/nebula/browse', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;

        const blocks = await MemberBlock.find({ memberId: member.memberId }).select('blockedMemberId').lean();
        const blockedIds = blocks.map(b => b.blockedMemberId);

        const pendingInvites = await NebulaInvite.find({
            $or: [{ fromMemberId: member.memberId }, { toMemberId: member.memberId }],
            status: 'PENDING'
        }).lean();
        const pendingMap = new Map();
        pendingInvites.forEach(inv => {
            const otherId = inv.fromMemberId === member.memberId ? inv.toMemberId : inv.fromMemberId;
            pendingMap.set(otherId, inv.fromMemberId === member.memberId ? 'SENT' : 'RECEIVED');
        });

        // Query ALL public members with discoverable: true (except blocked accounts)
        const publicMembers = await Member.find({
            discoverable: true,
            memberId: { $nin: blockedIds },
            revokedAt: null
        })
        .select('memberId handle displayName googleDisplayName avatarUrl twigiRenderUrl profileEmojiUrl avatarMode bio nebulaSeed lastSeenAt')
        .limit(100)
        .lean();

        const now = Date.now();
        const motes = publicMembers.map(m => ({
            memberId: m.memberId,
            handle: m.handle || 'star',
            displayName: m.displayName || m.googleDisplayName || `@${m.handle}`,
            avatarUrl: m.avatarUrl || null,
            twigiRenderUrl: m.avatarMode === 'TWIGI' ? (m.twigiRenderUrl || null) : null,
            profileEmojiUrl: m.profileEmojiUrl || null,
            avatarMode: m.avatarMode || 'EMOJI',
            bio: m.bio || null,
            nebulaSeed: typeof m.nebulaSeed === 'number' ? m.nebulaSeed : (m.memberId ? m.memberId.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0) : 42),
            isRecentlyActive: m.lastSeenAt ? (now - new Date(m.lastSeenAt).getTime() < 30 * 60 * 1000) : false,
            inviteStatus: m.memberId === member.memberId ? 'SELF' : (pendingMap.get(m.memberId) || 'NONE')
        }));

        res.json({ motes });
    } catch (err) {
        console.error('Error browsing nebula:', err);
        res.status(500).json({ error: 'Failed to load nebula' });
    }
});

app.get('/api/nebula/search', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;

        const q = String(req.query.q || '').trim().toLowerCase().replace(/^@/, '');
        if (!q) return res.json({ results: [] });

        const blocks = await MemberBlock.find({ memberId: member.memberId }).select('blockedMemberId').lean();
        const blockedIds = blocks.map(b => b.blockedMemberId);
        blockedIds.push(member.memberId);

        const escapedQuery = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const regex = new RegExp(escapedQuery, 'i');

        const matches = await Member.find({
            discoverable: true,
            memberId: { $nin: blockedIds },
            revokedAt: null,
            $or: [
                { handle: { $regex: '^' + escapedQuery, $options: 'i' } },
                { displayName: { $regex: regex } }
            ]
        })
        .select('memberId handle displayName googleDisplayName avatarUrl twigiRenderUrl profileEmojiUrl avatarMode bio nebulaSeed lastSeenAt')
        .limit(20)
        .lean();

        const pendingInvites = await NebulaInvite.find({
            $or: [{ fromMemberId: member.memberId }, { toMemberId: member.memberId }],
            status: 'PENDING'
        }).lean();
        const pendingMap = new Map();
        pendingInvites.forEach(inv => {
            const otherId = inv.fromMemberId === member.memberId ? inv.toMemberId : inv.fromMemberId;
            pendingMap.set(otherId, inv.fromMemberId === member.memberId ? 'SENT' : 'RECEIVED');
        });

        const now = Date.now();
        const results = matches.map(m => ({
            memberId: m.memberId,
            handle: m.handle || 'star',
            displayName: m.displayName || m.googleDisplayName || `@${m.handle}`,
            avatarUrl: m.avatarUrl || null,
            twigiRenderUrl: m.avatarMode === 'TWIGI' ? (m.twigiRenderUrl || null) : null,
            profileEmojiUrl: m.profileEmojiUrl || null,
            bio: m.bio || null,
            nebulaSeed: typeof m.nebulaSeed === 'number' ? m.nebulaSeed : 42,
            isRecentlyActive: m.lastSeenAt ? (now - new Date(m.lastSeenAt).getTime() < 30 * 60 * 1000) : false,
            inviteStatus: pendingMap.get(m.memberId) || 'NONE'
        }));

        res.json({ results });
    } catch (err) {
        console.error('Error searching nebula:', err);
        res.status(500).json({ error: 'Failed to search nebula' });
    }
});

app.post('/api/nebula/invite', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;

        const { targetMemberId, targetHandle } = req.body || {};
        let target = null;
        if (targetMemberId) {
            target = await Member.findOne({ memberId: targetMemberId, revokedAt: null });
        } else if (targetHandle) {
            target = await Member.findOne({ handle: String(targetHandle).trim().toLowerCase().replace(/^@/, ''), revokedAt: null });
        }
        if (!target) return res.status(404).json({ error: 'Target member not found' });
        if (target.memberId === member.memberId) return res.status(400).json({ error: 'Cannot invite yourself' });

        const alreadyConnected = await haveCommonActiveConnection(member.memberId, target.memberId);
        if (alreadyConnected) return res.status(400).json({ error: 'Already connected with this member' });

        let invite = await NebulaInvite.findOne({
            fromMemberId: member.memberId,
            toMemberId: target.memberId,
            status: 'PENDING'
        });

        if (!invite) {
            invite = new NebulaInvite({
                inviteId: 'ninv_' + crypto.randomBytes(8).toString('hex'),
                fromMemberId: member.memberId,
                toMemberId: target.memberId,
                fromHandle: member.handle,
                toHandle: target.handle,
                fromDisplayName: member.displayName || member.googleDisplayName || `@${member.handle}`,
                fromAvatarUrl: member.avatarUrl || null,
                fromTwigiUrl: member.avatarMode === 'TWIGI' ? member.twigiRenderUrl : null,
                fromProfileEmojiUrl: member.profileEmojiUrl || null,
                status: 'PENDING'
            });
            await invite.save();

            sendFcmPushToPartner(target.memberId, {
                type: 'nebula_invite',
                inviteId: invite.inviteId,
                fromHandle: member.handle,
                fromDisplayName: member.displayName || `@${member.handle}`,
                actionType: 'nebula_invite'
            });
        }

        res.json({ success: true, inviteId: invite.inviteId, status: invite.status });
    } catch (err) {
        console.error('Error sending nebula invite:', err);
        res.status(500).json({ error: 'Failed to send invite' });
    }
});

app.post('/api/nebula/invite/respond', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;

        const { inviteId, accept } = req.body || {};
        const invite = await NebulaInvite.findOne({ inviteId });
        if (!invite) return res.status(404).json({ error: 'Invite not found' });
        if (invite.toMemberId !== member.memberId) return res.status(403).json({ error: 'Unauthorized to respond to this invite' });
        if (invite.status !== 'PENDING') return res.json({ success: true, status: invite.status });

        if (accept) {
            const sender = await Member.findOne({ memberId: invite.fromMemberId });
            if (!sender) return res.status(404).json({ error: 'Sender no longer exists' });

            const connectionCode = generateConnectionCode();
            const session = new Session({
                connectionCode,
                creatorDeviceId: sender.primaryDeviceId || 'nebula_creator',
                relationshipType: 'ROMANTIC',
                participants: [
                    { memberId: sender.memberId, deviceId: sender.primaryDeviceId || 'nebula_p1', role: 'CREATOR', partnerLabel: sender.displayName },
                    { memberId: member.memberId, deviceId: member.primaryDeviceId || 'nebula_p2', role: 'PARTNER', partnerLabel: member.displayName }
                ]
            });
            await session.save();

            await ConnectionMembership.create([
                {
                    memberId: sender.memberId,
                    connectionCode,
                    role: 'CREATOR',
                    origin: 'NEBULA',
                    trustRing: 3,
                    partnerDisplayNameCache: member.displayName || member.googleDisplayName || `@${member.handle}`
                },
                {
                    memberId: member.memberId,
                    connectionCode,
                    role: 'PARTNER',
                    origin: 'NEBULA',
                    trustRing: 3,
                    partnerDisplayNameCache: sender.displayName || sender.googleDisplayName || `@${sender.handle}`
                }
            ]);

            invite.status = 'ACCEPTED';
            invite.connectionCode = connectionCode;
            invite.respondedAt = new Date();
            await invite.save();

            sendFcmPushToPartner(sender.memberId, {
                type: 'nebula_invite_accepted',
                connectionId: connectionCode,
                partnerName: member.displayName || `@${member.handle}`,
                actionType: 'nebula_invite_accepted'
            });

            res.json({ success: true, status: 'ACCEPTED', connectionCode });
        } else {
            invite.status = 'DECLINED';
            invite.respondedAt = new Date();
            await invite.save();
            res.json({ success: true, status: 'DECLINED' });
        }
    } catch (err) {
        console.error('Error responding to nebula invite:', err);
        res.status(500).json({ error: 'Failed to respond to invite' });
    }
});

app.get('/api/nebula/invites/pending', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;

        const incoming = await NebulaInvite.find({
            toMemberId: member.memberId,
            status: 'PENDING'
        }).sort({ createdAt: -1 }).limit(20).lean();

        const senderIds = incoming.map(i => i.fromMemberId);
        const senders = await Member.find({ memberId: { $in: senderIds } }).lean();
        const senderMap = new Map(senders.map(s => [s.memberId, s]));

        const results = incoming.map(inv => {
            const s = senderMap.get(inv.fromMemberId);
            return {
                inviteId: inv.inviteId,
                fromMemberId: inv.fromMemberId,
                handle: inv.fromHandle || s?.handle || 'visitor',
                displayName: inv.fromDisplayName || s?.displayName || 'Cosmic Visitor',
                avatarUrl: inv.fromAvatarUrl || s?.avatarUrl || null,
                twigiRenderUrl: inv.fromTwigiUrl || (s?.avatarMode === 'TWIGI' ? s.twigiRenderUrl : null),
                profileEmojiUrl: inv.fromProfileEmojiUrl || s?.profileEmojiUrl || null,
                avatarMode: s?.avatarMode || 'EMOJI',
                bio: s?.bio || 'Reached out to join your galaxy ✨',
                createdAt: inv.createdAt
            };
        });

        res.json({ success: true, invites: results });
    } catch (err) {
        console.error('Error fetching pending nebula invites:', err);
        res.status(500).json({ error: 'Failed to get invites' });
    }
});

app.post('/api/nebula/block', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;
        const { targetMemberId } = req.body || {};
        if (!targetMemberId || targetMemberId === member.memberId) return res.status(400).json({ error: 'Invalid target' });

        await MemberBlock.findOneAndUpdate(
            { memberId: member.memberId, blockedMemberId: targetMemberId },
            { memberId: member.memberId, blockedMemberId: targetMemberId, createdAt: new Date() },
            { upsert: true }
        );
        res.json({ success: true });
    } catch (err) {
        console.error('Error blocking member:', err);
        res.status(500).json({ error: 'Failed to block' });
    }
});

app.post('/api/nebula/report', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;
        const { targetMemberId, reason, note } = req.body || {};
        if (!targetMemberId || !reason) return res.status(400).json({ error: 'Missing reason or target' });

        await MemberReport.create({
            reporterId: member.memberId,
            reportedId: targetMemberId,
            reason: sanitizeText(reason, 120),
            note: sanitizeText(note || '', 300)
        });
        res.json({ success: true });
    } catch (err) {
        console.error('Error reporting member:', err);
        res.status(500).json({ error: 'Failed to report' });
    }
});

// ── Our Nest (Cozy Shared Twigi Room) Endpoints ───────────────────────────
app.get('/api/nest/:connectionCode', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const code = sanitizeText(req.params.connectionCode || '', 120).toLowerCase();
        if (!code) return res.status(400).json({ error: 'Connection code required' });

        let room = await NestRoom.findOne({ connectionCode: code }).lean();
        if (!room) {
            room = await NestRoom.create({ connectionCode: code });
        }
        res.json({ success: true, room });
    } catch (err) {
        console.error('Error getting nest room:', err);
        res.status(500).json({ error: 'Failed to get nest room' });
    }
});

app.post('/api/nest/decor', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const { connectionCode, wallpaper, flooring, roomMood, furniture } = req.body || {};
        const code = sanitizeText(connectionCode || '', 120).toLowerCase();
        if (!code) return res.status(400).json({ error: 'Connection code required' });

        const update = {};
        if (wallpaper) update.wallpaper = sanitizeText(wallpaper, 60);
        if (flooring) update.flooring = sanitizeText(flooring, 60);
        if (roomMood) update.roomMood = sanitizeText(roomMood, 40);
        if (Array.isArray(furniture)) update.furniture = furniture.slice(0, 30);

        const room = await NestRoom.findOneAndUpdate(
            { connectionCode: code },
            { $set: update },
            { new: true, upsert: true, setDefaultsOnInsert: true }
        ).lean();

        // Broadcast layout update to active sockets in this connection
        broadcastToConnection(code, () => ({
            type: 'nest_room_update',
            action: 'decor_changed',
            connectionCode: code,
            room
        }));

        res.json({ success: true, room });
    } catch (err) {
        console.error('Error updating nest decor:', err);
        res.status(500).json({ error: 'Failed to update decor' });
    }
});

app.post('/api/nest/notes', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;
        const { connectionCode, action, noteId, text, drawingUrl, color } = req.body || {};
        const code = sanitizeText(connectionCode || '', 120).toLowerCase();
        if (!code) return res.status(400).json({ error: 'Connection code required' });

        let room = await NestRoom.findOne({ connectionCode: code });
        if (!room) room = new NestRoom({ connectionCode: code });

        if (action === 'DELETE' && noteId) {
            room.fridgeNotes = (room.fridgeNotes || []).filter(n => n.id !== noteId);
        } else if (action === 'ADD' || (!action && (text || drawingUrl))) {
            const newNote = {
                id: 'fn_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
                authorId: member.memberId,
                authorName: member.displayName || member.googleDisplayName || `@${member.handle}`,
                text: sanitizeText(text || '', 500),
                drawingUrl: drawingUrl ? sanitizeText(drawingUrl, 512) : null,
                color: sanitizeText(color || '#FEF08A', 20),
                createdAt: new Date().toISOString()
            };
            room.fridgeNotes = [newNote, ...(room.fridgeNotes || [])].slice(0, 50);
        }
        await room.save();

        broadcastToConnection(code, () => ({
            type: 'nest_room_update',
            action: 'notes_updated',
            connectionCode: code,
            fridgeNotes: room.fridgeNotes
        }));

        res.json({ success: true, fridgeNotes: room.fridgeNotes });
    } catch (err) {
        console.error('Error updating fridge notes:', err);
        res.status(500).json({ error: 'Failed to update fridge notes' });
    }
});

app.post('/api/nest/pet/interact', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const member = auth.member;
        const { connectionCode, action } = req.body || {};
        const code = sanitizeText(connectionCode || '', 120).toLowerCase();
        if (!code) return res.status(400).json({ error: 'Connection code required' });

        let room = await NestRoom.findOne({ connectionCode: code });
        if (!room) room = new NestRoom({ connectionCode: code });

        const pet = room.pet || { name: 'Mochi', type: 'cat', happiness: 100, hunger: 80 };
        if (action === 'FEED') {
            pet.hunger = Math.min(100, (pet.hunger || 80) + 20);
            pet.happiness = Math.min(100, (pet.happiness || 90) + 10);
            pet.lastFedAt = new Date();
        } else {
            // PET / CUDDLE
            pet.happiness = Math.min(100, (pet.happiness || 90) + 15);
            pet.lastPettedAt = new Date();
        }
        room.pet = pet;
        room.markModified('pet');
        await room.save();

        broadcastToConnection(code, () => ({
            type: 'nest_emote',
            action: 'pet_interact',
            actorName: member.displayName || member.googleDisplayName || 'Partner',
            petAction: action || 'PET',
            connectionCode: code,
            pet
        }));

        res.json({ success: true, pet });
    } catch (err) {
        console.error('Error interacting with pet:', err);
        res.status(500).json({ error: 'Failed to interact with pet' });
    }
});

// Reliable connection/group deletion. The WS `disconnect` frame can be dropped when the
// socket isn't open, which left "deleted" groups alive server-side (they reappeared on
// every bootstrap). This endpoint does the same work over HTTP: creators hard-delete the
// whole connection; members archive just their own membership.
app.post('/api/client/connections/archive', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const code = sanitizeText(req.body?.connectionCode || '', 120).toLowerCase();
        if (!code) return res.status(400).json({ error: 'connectionCode required.' });

        const session = await Session.findOne({ connectionCode: code });
        if (session) {
            // Notify live partner sockets that the connection was archived/unlinked.
            broadcastToConnection(code, () => ({
                type: 'connection_removed', connectionCode: code, connectionId: code, reason: 'Partner unlinked relationship'
            }));
        }

        const me = (session?.participants || []).find(p => p.memberId === auth.member.memberId);
        const isCreator = (me?.role === 'CREATOR')
            || (auth.member.knownDeviceIds || []).some(d => normalizeId(d) === normalizeId(session?.creatorDeviceId));

        if (isCreator && session) {
            await ConnectionMembership.deleteMany({ connectionCode: code });
            await Session.updateOne(
                { _id: session._id },
                { $set: { isDeleted: true, deletedAt: new Date(), participants: [], events: [] } }
            );
            connections.delete(code);
            logEvent('connection.deleted.http', { connectionCode: code, memberId: auth.member.memberId });
            return res.json({ ok: true, deleted: true });
        }

        await ConnectionMembership.updateOne(
            { memberId: auth.member.memberId, connectionCode: code },
            { $set: { archivedAt: new Date() } }
        );
        if (session) {
            await Session.updateOne(
                { _id: session._id },
                { $pull: { participants: { memberId: auth.member.memberId } } }
            );
        }
        logEvent('connection.left.http', { connectionCode: code, memberId: auth.member.memberId });
        res.json({ ok: true, deleted: false });
    } catch (error) {
        logEvent('connection.archive.failed', { error: error.message });
        res.status(500).json({ error: error.message });
    }
});

// Atomic group creation. The old flow registered the session via a WS frame and then
// tried to add members/emoji over HTTP ~1s later — the session often didn't exist yet,
// so those calls 404'd silently (empty roster, no group emoji). This creates the session,
// creator membership, shared emoji, and invited members in ONE authoritative request.
app.post('/api/client/groups/create', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const groupCode = sanitizeText(req.body?.groupCode || '', 120).toLowerCase();
        const name = sanitizeText(req.body?.name || 'Our Group', 80) || 'Our Group';
        const emojiUrl = sanitizeText(req.body?.emojiUrl || '', 512) || null;
        const deviceId = sanitizeText(req.body?.deviceId || auth.member.primaryDeviceId || '', 120);
        const deviceName = sanitizeText(req.body?.deviceName || auth.member.deviceName || 'Device', 80);
        const viaCodes = Array.isArray(req.body?.viaCodes)
            ? req.body.viaCodes.map(v => sanitizeText(v, 120).toLowerCase()).filter(Boolean).slice(0, 24)
            : [];
        if (!groupCode || !deviceId) {
            return res.status(400).json({ error: 'groupCode and deviceId required.' });
        }

        const existing = await Session.findOne({ connectionCode: groupCode }).lean();
        if (existing?.isDeleted) {
            return res.status(409).json({ error: 'This code belonged to a deleted connection.' });
        }
        if (existing) {
            return res.status(409).json({ error: 'Connection code already in use.' });
        }

        // Plan enforcement — mirror the WS create gates.
        const plan = await resolvePlanForMember(auth.member);
        if (!plan.features.groupConnections) {
            return res.status(402).json({
                error: 'Group connections are a Gigi Plus feature. Upgrade to create groups.',
                code: 'PLAN_FEATURE_LOCKED',
                tier: plan.tier
            });
        }
        if (plan.maxConnections > 0) {
            const creatorCount = await ConnectionMembership.countDocuments({
                memberId: auth.member.memberId, role: 'CREATOR', archivedAt: null
            });
            if (creatorCount >= plan.maxConnections) {
                return res.status(402).json({
                    error: `You've reached your plan limit of ${plan.maxConnections} connections.`,
                    code: 'CONNECTION_LIMIT_REACHED',
                    limit: plan.maxConnections,
                    tier: plan.tier
                });
            }
        }

        // Build the participant roster: creator + each via-connection's partner.
        const participants = [{
            clientId: uuidv4(),
            deviceId,
            deviceName,
            partnerLabel: name,
            memberId: auth.member.memberId,
            role: 'CREATOR',
            connectedAt: new Date()
        }];
        const added = [];
        for (const via of viaCodes) {
            const viaSession = await Session.findOne({ connectionCode: via }).lean();
            if (!viaSession || viaSession.isDeleted) continue;
            const callerInVia = (viaSession.participants || []).some(p => p.memberId === auth.member.memberId);
            if (!callerInVia) continue;
            const partner = getPartnerParticipant(viaSession, auth.member.memberId, null);
            if (!partner?.memberId) continue;
            if (participants.some(p => p.memberId === partner.memberId)) continue;
            const pm = await Member.findOne({ memberId: partner.memberId, revokedAt: null }).lean();
            const label = sanitizeText(pm?.displayName || partner.partnerLabel || partner.deviceName || 'Member', 80);
            participants.push({
                clientId: uuidv4(),
                deviceId: partner.deviceId || null,
                deviceName: sanitizeText(partner.deviceName || label, 80),
                partnerLabel: label,
                memberId: partner.memberId,
                role: 'PARTNER',
                connectedAt: new Date()
            });
            added.push({ memberId: partner.memberId, name: label });
        }

        await Session.create({
            connectionCode: groupCode,
            dbName: `session_group_${groupCode}`,
            creatorDeviceId: deviceId,
            relationshipType: 'GROUP',
            groupEmojiUrl: emojiUrl,
            participants,
            events: [{ type: 'created', timestamp: new Date(), data: { deviceId, deviceName, partnerLabel: name } }]
        });
        await upsertConnectionMembership({
            memberId: auth.member.memberId,
            connectionCode: groupCode,
            role: 'CREATOR',
            partnerDisplayNameCache: name
        });
        for (const m of added) {
            await upsertConnectionMembership({
                memberId: m.memberId,
                connectionCode: groupCode,
                role: 'PARTNER',
                partnerDisplayNameCache: m.name
            });
        }

        logEvent('group.created.http', { groupCode, memberId: auth.member.memberId, members: added.length });
        res.json({ ok: true, groupCode, added });
    } catch (error) {
        logEvent('group.create.failed', { error: error.message });
        res.status(500).json({ error: error.message });
    }
});

// Reliable group member add. At group creation the app names the 1-1 connections whose
// partners join the group; membership is written here (HTTP), with the WS group_invite
// kept only as a live notification. Fixes members silently missing from the roster.
app.post('/api/client/groups/add-members', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const groupCode = sanitizeText(req.body?.groupCode || '', 120).toLowerCase();
        const viaCodes = Array.isArray(req.body?.viaCodes)
            ? req.body.viaCodes.map(v => sanitizeText(v, 120).toLowerCase()).filter(Boolean).slice(0, 24)
            : [];
        if (!groupCode || viaCodes.length === 0) {
            return res.status(400).json({ error: 'groupCode and viaCodes required.' });
        }
        const groupSession = await Session.findOne({ connectionCode: groupCode });
        if (!groupSession || groupSession.isDeleted) {
            return res.status(404).json({ error: 'Group not found.' });
        }
        const callerInGroup = (groupSession.participants || []).some(p => p.memberId === auth.member.memberId);
        if (!callerInGroup) return res.status(403).json({ error: 'Group access denied.' });

        const added = [];
        for (const via of viaCodes) {
            const viaSession = await Session.findOne({ connectionCode: via }).lean();
            if (!viaSession) continue;
            // Only connections the caller belongs to can be used to pull partners in.
            const callerInVia = (viaSession.participants || []).some(p => p.memberId === auth.member.memberId);
            if (!callerInVia) continue;
            const partner = getPartnerParticipant(viaSession, auth.member.memberId, null);
            if (!partner?.memberId) continue;
            const already = (groupSession.participants || []).some(p => p.memberId === partner.memberId);
            if (already) continue;
            const pm = await Member.findOne({ memberId: partner.memberId, revokedAt: null }).lean();
            const label = sanitizeText(pm?.displayName || partner.partnerLabel || partner.deviceName || 'Member', 80);
            await Session.updateOne({ _id: groupSession._id }, {
                $push: {
                    participants: {
                        clientId: uuidv4(),
                        deviceId: partner.deviceId || null,
                        deviceName: sanitizeText(partner.deviceName || label, 80),
                        partnerLabel: label,
                        memberId: partner.memberId,
                        role: 'PARTNER',
                        connectedAt: new Date()
                    }
                }
            });
            await upsertConnectionMembership({
                memberId: partner.memberId,
                connectionCode: groupCode,
                role: 'PARTNER',
                partnerDisplayNameCache: label
            });
            groupSession.participants.push({ memberId: partner.memberId });
            added.push({ memberId: partner.memberId, name: label });
        }
        logEvent('group.members.added.http', { groupCode, count: added.length });
        res.json({ ok: true, added });
    } catch (error) {
        logEvent('group.addmembers.failed', { error: error.message });
        res.status(500).json({ error: error.message });
    }
});

// Set the group's shared animated emoji (any member; shown to everyone). Broadcast live
// to connected members; offline members pick it up from bootstrap (partnerEmojiUrl).
app.post('/api/client/groups/emoji', async (req, res) => {
    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;
        const groupCode = sanitizeText(req.body?.groupCode || '', 120).toLowerCase();
        const emojiUrl = sanitizeText(req.body?.emojiUrl || '', 512);
        if (!groupCode || !emojiUrl) return res.status(400).json({ error: 'groupCode and emojiUrl required.' });

        const session = await Session.findOne({ connectionCode: groupCode });
        if (!session || session.isDeleted) return res.status(404).json({ error: 'Group not found.' });
        const callerInGroup = (session.participants || []).some(p => p.memberId === auth.member.memberId);
        if (!callerInGroup) return res.status(403).json({ error: 'Group access denied.' });

        await Session.updateOne({ _id: session._id }, { $set: { groupEmojiUrl: emojiUrl } });
        broadcastToConnection(groupCode, () => ({
            type: 'group_emoji', connectionId: groupCode, emojiUrl, sentAt: Date.now()
        }));
        res.json({ ok: true });
    } catch (error) {
        logEvent('group.emoji.failed', { error: error.message });
        res.status(500).json({ error: error.message });
    }
});

app.post('/api/client/recover', async (req, res) => {
    try {
        const deviceId = sanitizeText(req.body?.deviceId || '', 120);
        const deviceName = sanitizeText(req.body?.deviceName || 'Unknown device', 80);
        const recoveryCode = req.body?.recoveryCode;

        if (!deviceId || !recoveryCode) {
            return res.status(400).json({ error: 'deviceId and recoveryCode are required' });
        }

        const member = await resolveMemberByRecoveryCode(recoveryCode);
        if (!member) {
            return res.status(404).json({
                error: 'Recovery code not found',
                code: 'RECOVERY_NOT_FOUND'
            });
        }

        await attachDeviceToMember(member, deviceId, deviceName);
        const response = await buildBootstrapResponse({
            member,
            deviceId,
            deviceName,
            ambiguous: false
        });
        res.json(response);
    } catch (error) {
        logEvent('recover.failed', { error: error.message });
        res.status(500).json({ error: error.message });
    }
});

app.post('/api/shared-alarms/upsert', async (req, res) => {
    if (await rejectMaintenanceHttp(res)) {
        return;
    }

    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;

        const connectionCode = sanitizeText(req.body?.connectionCode || '', 120).toLowerCase();
        const alarmId = sanitizeText(req.body?.alarmId || '', 120) || uuidv4();
        const title = sanitizeText(req.body?.title || '', 120);
        const note = sanitizeText(req.body?.note || '', 320) || null;
        const dueAt = Number(req.body?.dueAt || 0);
        const recurrencePattern = sanitizeText(req.body?.recurrencePattern || '', 40) || null;
        const customIntervalMillis = Number.isFinite(Number(req.body?.customIntervalMillis))
            ? Number(req.body.customIntervalMillis)
            : null;
        const repeatStartHour = Number.isFinite(Number(req.body?.repeatStartHour)) ? Number(req.body.repeatStartHour) : null;
        const repeatStartMinute = Number.isFinite(Number(req.body?.repeatStartMinute)) ? Number(req.body.repeatStartMinute) : null;
        const repeatEndHour = Number.isFinite(Number(req.body?.repeatEndHour)) ? Number(req.body.repeatEndHour) : null;
        const repeatEndMinute = Number.isFinite(Number(req.body?.repeatEndMinute)) ? Number(req.body.repeatEndMinute) : null;

        if (!connectionCode || !title || !dueAt) {
            return res.status(400).json({ error: 'connectionCode, title, and dueAt are required.' });
        }

        const membership = await ensureMembershipAccess(auth.member.memberId, connectionCode);
        if (!membership) {
            return res.status(403).json({ error: 'Connection access denied.' });
        }

        // Plan enforcement: reminder count (new alarms only) and recurring-alarm feature
        const plan = await resolvePlanForMember(auth.member);
        const existingAlarm = await SharedAlarm.findOne({ alarmId }).lean();
        if (!existingAlarm && plan.maxReminders > 0) {
            const activeCount = await SharedAlarm.countDocuments({
                ownerMemberId: auth.member.memberId,
                isActive: true,
                deletedAt: null
            });
            if (activeCount >= plan.maxReminders) {
                return res.status(402).json({
                    error: `You've reached your plan limit of ${plan.maxReminders} reminders. Upgrade to add more.`,
                    code: 'PLAN_LIMIT_REACHED',
                    limit: plan.maxReminders
                });
            }
        }
        if (recurrencePattern && !plan.features.recurringAlarms) {
            return res.status(402).json({
                error: 'Recurring alarms are a paid feature. Upgrade to use them.',
                code: 'PLAN_FEATURE_LOCKED'
            });
        }

        const alarm = await SharedAlarm.findOneAndUpdate(
            { alarmId },
            {
                $set: {
                    connectionCode,
                    title,
                    note,
                    dueAt: new Date(dueAt),
                    recurrencePattern,
                    customIntervalMillis,
                    repeatStartHour,
                    repeatStartMinute,
                    repeatEndHour,
                    repeatEndMinute,
                    ownerMemberId: auth.member.memberId,
                    ownerDisplayName: sanitizeText(auth.member.displayName || auth.member.deviceName || 'Partner', 80),
                    isActive: true,
                    deletedAt: null
                },
                $setOnInsert: {
                    alarmId
                }
            },
            { upsert: true, new: true, setDefaultsOnInsert: true }
        );

        await appendSessionEvent(connectionCode, 'shared_alarm_upserted', {
            alarmId: alarm.alarmId,
            title: alarm.title,
            note: alarm.note,
            dueAt: toTimestampMs(alarm.dueAt),
            recurrencePattern: alarm.recurrencePattern || null,
            ownerMemberId: auth.member.memberId,
            ownerDisplayName: sanitizeText(auth.member.displayName || auth.member.deviceName || 'Partner', 80)
        });

        await broadcastSharedAlarmUpsert(alarm);
        res.json({
            ok: true,
            alarm: serializeSharedAlarm(alarm)
        });
    } catch (error) {
        logEvent('shared_alarm.upsert.failed', { error: error.message });
        res.status(500).json({ error: 'Failed to save shared alarm.' });
    }
});

app.post('/api/shared-alarms/delete', async (req, res) => {
    if (await rejectMaintenanceHttp(res)) {
        return;
    }

    try {
        const auth = await requireAuthenticatedMember(req, res);
        if (!auth) return;

        const connectionCode = sanitizeText(req.body?.connectionCode || '', 120).toLowerCase();
        const alarmId = sanitizeText(req.body?.alarmId || '', 120);
        if (!connectionCode || !alarmId) {
            return res.status(400).json({ error: 'connectionCode and alarmId are required.' });
        }

        const membership = await ensureMembershipAccess(auth.member.memberId, connectionCode);
        if (!membership) {
            return res.status(403).json({ error: 'Connection access denied.' });
        }

        await SharedAlarm.updateOne(
            { alarmId, connectionCode },
            {
                $set: {
                    isActive: false,
                    deletedAt: new Date()
                }
            }
        );

        await appendSessionEvent(connectionCode, 'shared_alarm_deleted', {
            alarmId,
            ownerMemberId: auth.member.memberId,
            ownerDisplayName: sanitizeText(auth.member.displayName || auth.member.deviceName || 'Partner', 80)
        });

        await broadcastSharedAlarmDeleted(connectionCode, alarmId);
        res.json({ ok: true, alarmId, connectionCode });
    } catch (error) {
        logEvent('shared_alarm.delete.failed', { error: error.message });
        res.status(500).json({ error: 'Failed to delete shared alarm.' });
    }
});

// --- MongoDB Management APIs ---

// 1. List Collections (Merged from both databases)
app.get('/api/db/collections', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const [sColl, gColl] = await Promise.all([
            screensaverConn.db.listCollections().toArray(),
            gigiConn.db.listCollections().toArray()
        ]);

        const merged = [
            ...sColl.map(c => `screensaver.${c.name}`),
            ...gColl.map(c => `gigi.${c.name}`)
        ].sort();

        res.json(merged);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// 2. Get Data from Collection
app.get('/api/db/collection/:fullName', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const [dbName, collName] = req.params.fullName.split('.');
        if (!collName) return res.status(400).json({ error: 'Format must be db.collection' });

        const targetConn = dbName === 'gigi' ? gigiConn : screensaverConn;
        const collection = targetConn.db.collection(collName);

        // Pagination support
        const page = parseInt(req.query.page) || 1;
        const limit = parseInt(req.query.limit) || 20;
        const skip = (page - 1) * limit;

        const total = await collection.countDocuments();
        const data = await collection.find({})
            .sort({ _id: -1 }) // Newest first
            .skip(skip)
            .limit(limit)
            .toArray();

        res.json({
            data,
            total,
            page,
            totalPages: Math.ceil(total / limit)
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// 3. Delete Document
app.delete('/api/db/collection/:fullName/:id', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const [dbName, collName] = req.params.fullName.split('.');
        const id = req.params.id;

        const targetConn = dbName === 'gigi' ? gigiConn : screensaverConn;
        const collection = targetConn.db.collection(collName);

        let query = { _id: id };
        try {
            const { ObjectId } = require('mongodb');
            query = { _id: new ObjectId(id) };
        } catch (e) { }

        const result = await collection.deleteOne(query);
        if (result.deletedCount === 1) {
            res.json({ success: true });
        } else {
            res.status(404).json({ error: 'Document not found' });
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// --- MongoDB Schemas ---

const SessionSchema = new mongoose.Schema({
    connectionCode: { type: String, index: true },
    participants: [{
        clientId: String,
        deviceId: String,
        deviceName: String,
        partnerLabel: String,
        memberId: String,
        role: { type: String, enum: ['CREATOR', 'PARTNER'], default: 'PARTNER' },
        connectedAt: { type: Date, default: Date.now }
    }],
    events: [mongoose.Schema.Types.Mixed],
    dbName: { type: String },
    creatorDeviceId: { type: String, index: true },
    relationshipType: { type: String, default: 'ROMANTIC' },
    // The group's chosen animated emoji (asset path or URL) — shared by every member.
    groupEmojiUrl: { type: String, default: null },
    createdAt: { type: Date, default: Date.now },
    isDeleted: { type: Boolean, default: false, index: true },
    deletedAt: { type: Date, default: null }
}, { timestamps: true });

const Session = screensaverConn.model('Session', SessionSchema);

const DeviceSchema = new mongoose.Schema({
    deviceId: { type: String, unique: true },
    deviceName: String,
    lastSeen: { type: Date, default: Date.now },
    isOnline: { type: Boolean, default: false },
    clientId: String,
    activeSessionCount: { type: Number, default: 0 },
    lastConnectionCode: { type: String, default: null },
    presenceUpdatedAt: { type: Date, default: Date.now }
}, { timestamps: true });

const Device = gigiConn.model('Device', DeviceSchema);

const MemberSchema = new mongoose.Schema({
    memberId: { type: String, unique: true, index: true },
    primaryDeviceId: { type: String, index: true },
    knownDeviceIds: [{ type: String }],
    phoneNumber: { type: String, unique: true, sparse: true, index: true },
    googleEmail: { type: String, unique: true, sparse: true, index: true },
    googleDisplayName: { type: String, default: null },
    displayName: String,
    gender: { type: String, enum: ['him', 'her', 'them'], default: null },
    avatarUrl: { type: String, default: null },
    // The member's chosen animated profile emoji (asset path or absolute URL).
    // Persisted so partners + new group members see it on login, not only live.
    profileEmojiUrl: { type: String, default: null },
    // ── Twigi (layered 2D avatar) ────────────────────────────────────────
    // Which identity the member shows to others; both are always kept.
    avatarMode: { type: String, enum: ['EMOJI', 'TWIGI'], default: 'EMOJI' },
    // The avatar's part/color selections (for re-editing + couple stickers).
    twigiConfig: { type: Object, default: null },
    // Server URL of the composited PNG — what actually syncs to other devices.
    twigiRenderUrl: { type: String, default: null },
    // Free-form per-user client settings blob (galaxy layout, per-connection emoji
    // overrides, renames, relationship-theme choices, self-quotes). Restored on login.
    prefsBlob: { type: Object, default: {} },
    themeSongTitle: { type: String, default: null },
    themeSongUrl: { type: String, default: null },
    restoreTokenHash: String,
    recoveryCodeHash: String,
    recoveryCodeMasked: String,
    deviceName: String,
    fcmToken: { type: String, default: null },
    fcmTokenLastUpdated: { type: Date, default: null },
    lastSeenAt: { type: Date, default: Date.now },
    revokedAt: { type: Date, default: null },
    // ── Monetization ──────────────────────────────────────────────────────
    // Tier drives the member's limits via the global PlanConfig defaults.
    tier: { type: String, enum: ['free', 'plus', 'pro'], default: 'free' },
    // Optional per-member overrides that win over the tier defaults. Any subset of
    // { maxConnections, maxGroupMembers, maxStrokes, maxReminders, maxCardsPerStack,
    //   historyDays, features: { ... } }.
    planOverrides: { type: Object, default: {} },
    planExpiresAt: { type: Date, default: null },
    // ── Nebula Discovery (Public Mode) ───────────────────────────────────
    discoverable: { type: Boolean, default: false, index: true },
    handle: { type: String, unique: true, sparse: true, index: true, lowercase: true, trim: true },
    bio: { type: String, default: null, maxlength: 80 },
    discoverableSince: { type: Date, default: null },
    nebulaSeed: { type: Number, default: () => Math.floor(Math.random() * 10000) }
}, { timestamps: true });
MemberSchema.index({ knownDeviceIds: 1 });

const ConnectionMembershipSchema = new mongoose.Schema({
    memberId: { type: String, index: true },
    connectionCode: { type: String, index: true },
    role: { type: String, enum: ['CREATOR', 'PARTNER'], default: 'PARTNER' },
    origin: { type: String, enum: ['INVITE', 'NEBULA'], default: 'INVITE' },
    trustRing: { type: Number, default: 0 },
    partnerDisplayNameCache: String,
    archivedAt: { type: Date, default: null },
    lastConnectedAt: { type: Date, default: Date.now }
}, { timestamps: true });
ConnectionMembershipSchema.index({ memberId: 1, connectionCode: 1 }, { unique: true });

const NebulaInviteSchema = new mongoose.Schema({
    inviteId: { type: String, unique: true, index: true },
    fromMemberId: { type: String, required: true, index: true },
    toMemberId: { type: String, required: true, index: true },
    fromHandle: { type: String, default: null },
    toHandle: { type: String, default: null },
    fromDisplayName: { type: String, default: null },
    fromAvatarUrl: { type: String, default: null },
    fromTwigiUrl: { type: String, default: null },
    fromProfileEmojiUrl: { type: String, default: null },
    status: { type: String, enum: ['PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED'], default: 'PENDING', index: true },
    connectionCode: { type: String, default: null },
    createdAt: { type: Date, default: Date.now, expires: 14 * 24 * 3600 },
    respondedAt: { type: Date, default: null }
}, { timestamps: true });
NebulaInviteSchema.index({ fromMemberId: 1, toMemberId: 1, status: 1 });

const MemberBlockSchema = new mongoose.Schema({
    memberId: { type: String, required: true, index: true },
    blockedMemberId: { type: String, required: true, index: true },
    createdAt: { type: Date, default: Date.now }
}, { timestamps: true });
MemberBlockSchema.index({ memberId: 1, blockedMemberId: 1 }, { unique: true });

const MemberReportSchema = new mongoose.Schema({
    reporterId: { type: String, required: true, index: true },
    reportedId: { type: String, required: true, index: true },
    reason: { type: String, required: true },
    note: { type: String, default: null },
    createdAt: { type: Date, default: Date.now }
}, { timestamps: true });

const NebulaInvite = mongoose.models.NebulaInvite || mongoose.model('NebulaInvite', NebulaInviteSchema);
const MemberBlock = mongoose.models.MemberBlock || mongoose.model('MemberBlock', MemberBlockSchema);
const MemberReport = mongoose.models.MemberReport || mongoose.model('MemberReport', MemberReportSchema);

const SharedAlarmSchema = new mongoose.Schema({
    alarmId: { type: String, unique: true, index: true },
    connectionCode: { type: String, index: true },
    title: String,
    note: String,
    dueAt: { type: Date, index: true },
    recurrencePattern: { type: String, default: null },
    customIntervalMillis: { type: Number, default: null },
    repeatStartHour: { type: Number, default: null },
    repeatStartMinute: { type: Number, default: null },
    repeatEndHour: { type: Number, default: null },
    repeatEndMinute: { type: Number, default: null },
    ownerMemberId: { type: String, index: true },
    ownerDisplayName: String,
    isActive: { type: Boolean, default: true },
    deletedAt: { type: Date, default: null }
}, { timestamps: true });
SharedAlarmSchema.index({ connectionCode: 1, isActive: 1, updatedAt: -1 });

const LoveCardStackSchema = new mongoose.Schema({
    stackId: { type: String, unique: true, index: true },
    connectionCode: { type: String, index: true },
    title: String,
    senderMemberId: { type: String, index: true },
    senderDisplayName: String,
    recipientMemberId: { type: String, index: true },
    status: { type: String, enum: ['SENT', 'OPENED', 'ANSWERED'], default: 'SENT' },
    theme: { type: String, default: null },
    previewText: { type: String, default: null },
    openedAt: { type: Date, default: null },
    answeredAt: { type: Date, default: null }
}, { timestamps: true });
LoveCardStackSchema.index({ connectionCode: 1, updatedAt: -1 });

const LoveCardItemSchema = new mongoose.Schema({
    cardId: { type: String, unique: true, index: true },
    stackId: { type: String, index: true },
    connectionCode: { type: String, index: true },
    type: { type: String, enum: ['CUTE_NOTE', 'QUESTION', 'MULTIPLE_CHOICE', 'ANIMATED_GIFT'], default: 'CUTE_NOTE' },
    prompt: String,
    choices: [{ type: String }],
    theme: { type: String, default: null },
    animationStyle: { type: String, default: null },
    decorationsJson: { type: String, default: null },
    sortOrder: { type: Number, default: 0 }
}, { timestamps: true });
LoveCardItemSchema.index({ stackId: 1, sortOrder: 1 });

const LoveCardResponseSchema = new mongoose.Schema({
    responseId: { type: String, unique: true, index: true },
    stackId: { type: String, index: true },
    cardId: { type: String, index: true },
    answerText: { type: String, default: null },
    selectedChoice: { type: String, default: null },
    emojiReaction: { type: String, default: null },
    answeredAt: { type: Date, default: Date.now },
    answeredByMemberId: { type: String, default: null }
}, { timestamps: true });
LoveCardResponseSchema.index({ stackId: 1, cardId: 1 }, { unique: true });

const ServerStateSchema = new mongoose.Schema({
    singletonKey: { type: String, unique: true, default: SERVER_STATE_KEY },
    mode: { type: String, enum: ['ONLINE', 'MAINTENANCE'], default: 'ONLINE' },
    message: { type: String, default: '' },
    updatedBy: { type: String, default: 'system' }
}, { timestamps: true });

const AuthOtpSchema = new mongoose.Schema({
    phoneNumber: { type: String, index: true },
    deviceId: String,
    deviceName: String,
    codeHash: String,
    codePreview: String,
    expiresAt: { type: Date, index: true },
    consumedAt: { type: Date, default: null },
    attempts: { type: Number, default: 0 }
}, { timestamps: true });
AuthOtpSchema.index({ phoneNumber: 1, createdAt: -1 });

const AuthSessionSchema = new mongoose.Schema({
    memberId: { type: String, index: true },
    sessionTokenHash: { type: String, unique: true, index: true },
    deviceId: { type: String, index: true },
    deviceName: String,
    expiresAt: { type: Date, index: true },
    lastSeenAt: { type: Date, default: Date.now },
    revokedAt: { type: Date, default: null }
}, { timestamps: true });
AuthSessionSchema.index({ memberId: 1, deviceId: 1, revokedAt: 1 });

const Member = gigiConn.model('Member', MemberSchema);
const ConnectionMembership = gigiConn.model('ConnectionMembership', ConnectionMembershipSchema);
const SharedAlarm = gigiConn.model('SharedAlarm', SharedAlarmSchema);
const LoveCardStack = gigiConn.model('LoveCardStack', LoveCardStackSchema);
const LoveCardItem = gigiConn.model('LoveCardItem', LoveCardItemSchema);
const LoveCardResponse = gigiConn.model('LoveCardResponse', LoveCardResponseSchema);
const ServerState = gigiConn.model('ServerState', ServerStateSchema);
const AuthOtp = gigiConn.model('AuthOtp', AuthOtpSchema);
const AuthSession = gigiConn.model('AuthSession', AuthSessionSchema);

// Web account-deletion requests (Play-required deletion path; processed by admin)
const DeletionRequestSchema = new mongoose.Schema({
    contact: { type: String, index: true },        // phone number or email the user typed
    contactType: { type: String, enum: ['phone', 'email'] },
    note: { type: String, default: null },
    requestIp: { type: String, default: null },
    status: { type: String, enum: ['pending', 'processed', 'rejected'], default: 'pending', index: true },
    processedAt: { type: Date, default: null }
}, { timestamps: true });
const DeletionRequest = gigiConn.model('DeletionRequest', DeletionRequestSchema);

// ─────────────────────────────────────────────────────────────────────────────
// MONETIZATION: plan tiers + per-member limits (admin-controlled)
// ─────────────────────────────────────────────────────────────────────────────
const PLAN_CATALOG = require('./plan_catalog');
const PLAN_NUMERIC_KEYS = PLAN_CATALOG.NUMERIC_KEYS;
const PLAN_FEATURE_KEYS = PLAN_CATALOG.FEATURE_KEYS;
const PLAN_TIERS = ['free', 'plus', 'pro'];
const DEFAULT_TIER_PLANS_UPGRADE_URL = 'https://gigi.iamanraj.com/upgrade';

// Default limits for each tier. The admin can override these live via /admin/data/plan-config.
// A numeric limit of 0 means "unlimited" (never blocked).
const DEFAULT_TIER_PLANS = PLAN_CATALOG.DEFAULT_TIER_PLANS;


const PlanConfigSchema = new mongoose.Schema({
    singletonKey: { type: String, unique: true, default: 'global' },
    tiers: { type: Object, default: () => DEFAULT_TIER_PLANS },
    tiers_order: { type: Array, default: () => ['free', 'plus', 'pro'] },
    upgradeUrl: { type: String, default: 'https://gigi.iamanraj.com/upgrade' }
}, { timestamps: true });
const PlanConfig = gigiConn.model('PlanConfig', PlanConfigSchema);

// ── Remote app settings (API keys, kill switches, release gating) ─────────────
const AppSettingsLib = require('./app_settings');
const AppSettingsSchema = new mongoose.Schema({
    singletonKey: { type: String, unique: true, default: 'global' },
    values: { type: Object, default: () => AppSettingsLib.defaults() }
}, { timestamps: true });
const AppSettings = gigiConn.model('AppSettings', AppSettingsSchema);

// ── Our Nest (Cozy Shared Twigi Room) Schema ──────────────────────────────
const DEFAULT_NEST_FURNITURE = [
    { id: 'f_desk', name: 'Dual Monitor Workstation', type: 'desk_computer', x: 0.26, y: 0.32, widthDp: 90, heightDp: 48 },
    { id: 'f_chair', name: 'Ergonomic Swivel Chair', type: 'office_chair', x: 0.26, y: 0.38, widthDp: 32, heightDp: 32 },
    { id: 'f_bookshelf', name: 'Packed Library Bookshelf', type: 'bookshelf_large', x: 0.08, y: 0.24, widthDp: 48, heightDp: 60 },
    { id: 'f_bulletin', name: 'Bulletin Pinboard', type: 'bulletin_board', x: 0.26, y: 0.16, widthDp: 54, heightDp: 30 },
    { id: 'f_bed', name: 'Cozy Canopy Bed', type: 'cozy_bed', x: 0.80, y: 0.28, widthDp: 75, heightDp: 70 },
    { id: 'f_nightstand', name: 'Bedside Lamp Stand', type: 'nightstand_lamp', x: 0.62, y: 0.24, widthDp: 28, heightDp: 36 },
    { id: 'f_ac', name: 'Wall Air Conditioner', type: 'ac_unit', x: 0.50, y: 0.14, widthDp: 56, heightDp: 22 },
    { id: 'f_rug', name: 'Cozy Hearth Rug', type: 'heart_rug', x: 0.30, y: 0.70, widthDp: 100, heightDp: 60 },
    { id: 'f_sofa', name: 'Sweetheart Loveseat', type: 'sweetheart_sofa', x: 0.30, y: 0.66, widthDp: 85, heightDp: 42 },
    { id: 'f_table', name: 'Coffee Table with Mugs', type: 'coffee_table', x: 0.30, y: 0.75, widthDp: 52, heightDp: 26 },
    { id: 'f_turntable', name: 'Vintage Vinyl Station', type: 'turntable_station', x: 0.08, y: 0.60, widthDp: 36, heightDp: 42 },
    { id: 'f_plant', name: 'Leafy Monstera Pot', type: 'potted_plant', x: 0.08, y: 0.82, widthDp: 32, heightDp: 44 },
    { id: 'f_fridge', name: 'Retro Pastel Mini-Fridge', type: 'mini_fridge', x: 0.84, y: 0.60, widthDp: 42, heightDp: 56 },
    { id: 'f_dining', name: 'Snack Counter Table', type: 'coffee_table', x: 0.74, y: 0.76, widthDp: 48, heightDp: 32 },
    { id: 'f_clock', name: 'Wall Clock', type: 'wall_clock', x: 0.84, y: 0.46, widthDp: 24, heightDp: 24 }
];

const NestRoomSchema = new mongoose.Schema({
    connectionCode: { type: String, unique: true, index: true, required: true },
    wallpaper: { type: String, default: 'apartment_light' },
    flooring: { type: String, default: 'office_grid' },
    roomMood: { type: String, default: 'cozy' },
    furniture: { type: Array, default: () => DEFAULT_NEST_FURNITURE },
    fridgeNotes: { type: Array, default: () => [] },
    pet: {
        type: Object,
        default: () => ({
            name: 'Mochi',
            type: 'cat',
            happiness: 100,
            hunger: 80,
            lastFedAt: new Date(),
            lastPettedAt: new Date()
        })
    }
}, { timestamps: true });
const NestRoom = gigiConn.model('NestRoom', NestRoomSchema);

let appSettingsCache = null;
async function getAppSettings(force = false) {
    if (appSettingsCache && !force) return appSettingsCache;
    let doc = null;
    if (isMongoReady(gigiConn)) {
        try {
            doc = await AppSettings.findOneAndUpdate(
                { singletonKey: 'global' },
                { $setOnInsert: { singletonKey: 'global', values: AppSettingsLib.defaults() } },
                { upsert: true, new: true, setDefaultsOnInsert: true }
            ).lean();
        } catch (e) {
            logEvent('app_settings.read_failed', { error: e.message });
        }
    }
    appSettingsCache = { ...AppSettingsLib.defaults(), ...(doc?.values || {}) };
    return appSettingsCache;
}

/** Push new settings to every live client so kill switches land without a restart. */
async function broadcastAppSettings() {
    try {
        const values = AppSettingsLib.forClient(await getAppSettings(true));
        const msg = JSON.stringify({ type: 'app_settings_update', settings: values });
        wss.clients.forEach(ws => { if (ws.readyState === WebSocket.OPEN) ws.send(msg); });
    } catch (e) {
        console.error('[broadcastAppSettings] failed:', e.message);
    }
}

/** Merge a stored (possibly partial) tier definition over the defaults so callers always get every key. */
function normalizeTierPlan(tier, stored) {
    const base = DEFAULT_TIER_PLANS[tier] || DEFAULT_TIER_PLANS.free;
    const s = stored && typeof stored === 'object' ? stored : {};
    const out = {};

    // Presentation + pricing. A tier the admin invented has no defaults to fall back
    // on, so the id doubles as its display name until they set one.
    out.meta = {};
    for (const m of PLAN_CATALOG.META) {
        const v = s.meta?.[m.key];
        const fallback = base.meta?.[m.key] !== undefined ? base.meta[m.key] : m.default;
        if (m.type === 'bool') out.meta[m.key] = typeof v === 'boolean' ? v : !!fallback;
        else if (m.type === 'number') out.meta[m.key] = Number.isFinite(Number(v)) ? Number(v) : Number(fallback) || 0;
        else out.meta[m.key] = typeof v === 'string' && v.trim() ? v.trim() : String(fallback || '');
    }
    if (!out.meta.displayName) {
        out.meta.displayName = tier.charAt(0).toUpperCase() + tier.slice(1);
    }
    for (const k of PLAN_NUMERIC_KEYS) {
        const v = Number(s[k]);
        out[k] = Number.isFinite(v) ? v : (base[k] !== undefined ? base[k] : 0);
    }
    out.features = {};
    for (const k of PLAN_FEATURE_KEYS) {
        const defaultVal = base.features?.[k] !== undefined
            ? base.features[k]
            : PLAN_CATALOG.DEFAULT_ON_FEATURES.has(k);
        out.features[k] = typeof s?.features?.[k] === 'boolean' ? s.features[k] : defaultVal;
    }
    return out;
}


/** Read the global plan config (creating it from defaults on first use), fully normalized. */
async function getPlanConfig() {
    let doc = null;
    if (isMongoReady(gigiConn)) {
        try {
            doc = await PlanConfig.findOneAndUpdate(
                { singletonKey: 'global' },
                { $setOnInsert: { singletonKey: 'global', tiers: DEFAULT_TIER_PLANS } },
                { upsert: true, new: true, setDefaultsOnInsert: true }
            ).lean();
        } catch (e) {
            logEvent('plan_config.read_failed', { error: e.message });
        }
    }
    // Seed from the built-in tiers ONLY when nothing has been saved yet. Merging them
    // in on every read is what made deleting `plus`/`pro` impossible — the row went
    // away in Mongo and was immediately resurrected here.
    const stored = doc?.tiers && Object.keys(doc.tiers).length ? doc.tiers : null;
    const rawTiers = stored || DEFAULT_TIER_PLANS;
    const tiers = {};
    for (const t of Object.keys(rawTiers)) {
        tiers[t] = normalizeTierPlan(t, rawTiers[t]);
    }
    // `free` is the fallback every member resolves to, so it must always exist.
    if (!tiers.free) tiers.free = normalizeTierPlan('free', DEFAULT_TIER_PLANS.free);

    const order = Array.isArray(doc?.tiers_order) ? doc.tiers_order.filter(t => tiers[t]) : [];
    for (const t of Object.keys(tiers)) if (!order.includes(t)) order.push(t);

    return { tiers, tiers_order: order, upgradeUrl: doc?.upgradeUrl || DEFAULT_TIER_PLANS_UPGRADE_URL };
}

/** Broadcast plan config updates to all live WebSocket connections */
async function broadcastPlanConfigUpdate() {
    try {
        const planConfig = await getPlanConfig();
        const msg = JSON.stringify({ type: 'plan_config_update', planConfig });
        wss.clients.forEach(ws => {
            if (ws.readyState === WebSocket.OPEN) {
                ws.send(msg);
            }
        });
    } catch (e) {
        console.error('[broadcastPlanConfigUpdate] failed:', e.message);
    }
}

/** Resolve the effective plan for a member: tier defaults merged with any per-member overrides. */
async function resolvePlanForMember(member) {
    // The global "free for everyone" switch short-circuits the whole tier system, so
    // nothing downstream has to know it's on — every limit and gate simply opens.
    const settings = await getAppSettings();
    if (settings?.freeForAll) {
        const open = PLAN_CATALOG.unlimitedPlan();
        return {
            tier: member?.tier || 'free',
            expiresAt: null,
            freeForAll: true,
            ...Object.fromEntries(PLAN_NUMERIC_KEYS.map(k => [k, open[k]])),
            features: open.features
        };
    }
    const cfg = await getPlanConfig();
    const availableTiers = Object.keys(cfg.tiers);
    let tier = availableTiers.includes(member?.tier) ? member.tier : 'free';
    if (!cfg.tiers[tier]) tier = availableTiers[0] || 'free';

    // Expired paid plans resolve as free everywhere (auto-downgrade).
    if (tier !== 'free' && member?.planExpiresAt && new Date(member.planExpiresAt) < new Date()) {
        tier = 'free';
        // Persist the downgrade opportunistically; resolution stays correct either way.
        if (typeof member.save === 'function') {
            member.tier = 'free';
            member.save().catch(e => logEvent('plan.downgrade_persist_failed', { memberId: member.memberId, error: e.message }));
        }
    }
    const base = cfg.tiers[tier] || cfg.tiers.free || DEFAULT_TIER_PLANS.free;
    const ov = member?.planOverrides && typeof member.planOverrides === 'object' ? member.planOverrides : {};
    const plan = { tier, expiresAt: member?.planExpiresAt ? new Date(member.planExpiresAt).toISOString() : null };
    for (const k of PLAN_NUMERIC_KEYS) {
        const v = Number(ov[k]);
        plan[k] = Number.isFinite(v) && ov[k] !== null && ov[k] !== '' ? v : base[k];
    }
    plan.features = {};
    for (const k of PLAN_FEATURE_KEYS) {
        plan.features[k] = typeof ov?.features?.[k] === 'boolean' ? ov.features[k] : base.features[k];
    }
    return plan;
}

/** Build the appConfig block the client expects in the bootstrap response. */
async function buildAppConfig(member) {
    const cfg = await getPlanConfig();
    const plan = await resolvePlanForMember(member);
    const appConfig = { plan, upgradeUrl: cfg.upgradeUrl };

    const settings = AppSettingsLib.forClient(await getAppSettings());

    // What the app is allowed to offer. Previously the upgrade sheet hardcoded
    // "Plus"/"Pro" and two Play product ids, so deleting a tier here left the app
    // still selling it. Now the sheet renders from exactly this list.
    const currentRank = cfg.tiers[plan.tier]?.meta?.sortOrder ?? 0;
    appConfig.monetizationEnabled = !settings.freeForAll;
    appConfig.upgradeOptions = settings.freeForAll ? [] : Object.entries(cfg.tiers)
        .filter(([id, t]) =>
            t.meta?.purchasable &&
            t.meta?.productId &&
            id !== plan.tier &&
            (t.meta?.sortOrder ?? 0) > currentRank
        )
        .sort((a, b) => (a[1].meta.sortOrder ?? 0) - (b[1].meta.sortOrder ?? 0))
        .map(([id, t]) => ({
            tierId: id,
            displayName: t.meta.displayName,
            emoji: t.meta.emoji,
            tagline: t.meta.tagline,
            priceLabel: t.meta.priceLabel,
            productId: t.meta.productId
        }));
    appConfig.settings = settings;

    // The admin-panel value wins; the env var stays as a fallback so nothing breaks
    // for anyone who hasn't filled the field in yet.
    const giphyKey = settings.giphyApiKey || process.env.GIPHY_API_KEY || process.env.GIPHY_KEY;
    if (giphyKey) appConfig.giphyApiKey = giphyKey;
    return appConfig;
}

// --- PER-CONNECTION DATABASE ARCHITECTURE ---
const connectionDbs = new Map();

/**
 * Gets or creates a dedicated MongoDB connection and models for a specific pair
 * Now async to resolve friendly DB names from the master session record.
 */
async function getPairModels(connectionCode) {
    if (!connectionCode) return null;
    const code = connectionCode.toLowerCase();

    if (connectionDbs.has(code)) {
        return connectionDbs.get(code);
    }

    // Try to find the friendly name from the session record (Lowercased lookup)
    const session = await Session.findOne({ connectionCode: code });
    let dbName = session?.dbName || `session_${code}`;

    console.log(`🍃 [DB-INIT] Initializing dedicated database: ${dbName}`);
    const dbUrl = `${MONGO_BASE_URL}/${dbName}`;
    const conn = mongoose.createConnection(dbUrl);

    // 3. Notifications Collection Only
    const NotificationSchema = new mongoose.Schema({
        from: String,
        deviceId: String,
        data: mongoose.Schema.Types.Mixed,
        createdAt: { type: Date, default: Date.now }
    });

    const ActionSchema = new mongoose.Schema({
        connectionCode: { type: String, index: true },
        messageId: { type: String, required: true },
        actionType: { type: String, required: true },
        senderDeviceId: String,
        recipientDeviceId: String,
        status: { type: String, default: 'accepted' },
        requiresDisplayReceipt: { type: Boolean, default: false },
        assetRef: String,
        createdAt: { type: Date, default: Date.now },
        acceptedAt: Date,
        deliveredAt: Date,
        displayedAt: Date,
        failedAt: Date,
        lastError: String
    }, { timestamps: true });
    ActionSchema.index({ connectionCode: 1, messageId: 1 }, { unique: true });

    const models = {
        Notification: conn.model('notifications', NotificationSchema),
        Action: conn.model('actions', ActionSchema),
        db: conn
    };

    connectionDbs.set(code, models);

    conn.on('connected', () => console.log(`✅ [DB] Connected to session_${code}`));
    conn.on('error', (err) => console.error(`❌ [DB] Error in session_${code}:`, err));

    return models;
}


function getSessionDir(connectionCode) {
    const dir = path.join(CAPTURES_DIR, connectionCode);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir);
    return dir;
}

// --- NOTIFICATION SCHEMA ---
const NotificationSchema = new mongoose.Schema({
    notificationId: { type: String, required: true }, // unique per notification on device
    deviceId: { type: String, required: true, index: true },
    connectionCode: { type: String, required: true, index: true },
    packageName: { type: String },
    title: { type: String },
    text: { type: String },
    timestamp: { type: Date, default: Date.now },
    iconPath: { type: String },
    isClearable: { type: Boolean, default: true }
}, { timestamps: true });

NotificationSchema.index({ connectionCode: 1, timestamp: -1 });
// Ensure notificationId is unique within a connection
NotificationSchema.index({ connectionCode: 1, deviceId: 1, notificationId: 1 }, { unique: true });

const RemoteNotification = gigiConn.model('RemoteNotification', NotificationSchema);

function persistNotificationIcon(connectionCode, notificationId, iconBase64) {
    if (!iconBase64 || !connectionCode || !notificationId) return null;
    try {
        const data = decodeBase64Payload(iconBase64);
        if (!data || data.length === 0) return null;

        const extension = '.png'; // Notifications usually use PNG
        const fileStem = sanitizeFileStem(`${connectionCode}_${notificationId}`, 'icon');
        const fileName = `${fileStem}${extension}`;
        const filePath = path.join(NOTIFICATION_ICONS_DIR, fileName);

        fs.writeFileSync(filePath, data);
        return `/notifications/icons/${fileName}`;
    } catch (err) {
        console.error('❌ Failed to persist notification icon:', err);
        return null;
    }
}

function persistMemberAvatar(memberId, avatarBase64, avatarMimeType = 'image/jpeg') {
    if (!memberId) return null;
    const data = decodeBase64Payload(avatarBase64);
    if (!data || data.length === 0 || data.length > 3 * 1024 * 1024) return null;
    const extension = mimeToExtension(avatarMimeType);
    const fileStem = sanitizeFileStem(memberId, 'member');
    fs.readdirSync(AVATARS_DIR)
        .filter(name => name.startsWith(`${fileStem}_`) || name === `${fileStem}${extension}`)
        .forEach(name => {
            const oldPath = path.join(AVATARS_DIR, name);
            if (fs.existsSync(oldPath)) {
                fs.unlinkSync(oldPath);
            }
        });
    const fileName = `${fileStem}_${Date.now()}${extension}`;
    const filePath = path.join(AVATARS_DIR, fileName);
    fs.writeFileSync(filePath, data);
    return `/avatars/${fileName}`;
}

/** Writes an already-rendered PNG buffer to the avatars dir (server-side Twigi render). */
function persistMemberAvatarBuffer(memberId, buffer, ext = 'png') {
    if (!memberId || !buffer || buffer.length === 0 || buffer.length > 3 * 1024 * 1024) return null;
    const safeExt = /^(png|gif|webp)$/.test(ext) ? ext : 'png';
    const fileStem = sanitizeFileStem(memberId, 'member');
    fs.readdirSync(AVATARS_DIR)
        .filter(name => name.startsWith(`${fileStem}_`) || name === `${fileStem}.png`)
        .forEach(name => {
            const oldPath = path.join(AVATARS_DIR, name);
            if (fs.existsSync(oldPath)) fs.unlinkSync(oldPath);
        });
    const fileName = `${fileStem}_${Date.now()}.${safeExt}`;
    fs.writeFileSync(path.join(AVATARS_DIR, fileName), buffer);
    return `/avatars/${fileName}`;
}

// Helper to update session participant uniquely
async function updateSessionParticipant(connectionCode, client, deviceId, deviceName, {
    memberId = null,
    role = null,
    partnerLabel = null
} = {}, relationshipType = null) {
    const code = connectionCode.toLowerCase();
    const session = await Session.findOne({ connectionCode: code });
    const sanitizedDeviceName = sanitizeText(deviceName || 'Unknown device', 80);
    const sanitizedPartnerLabel = sanitizeText(partnerLabel || '', 80) || null;
    const normalizedRole = normalizeRole(
        role,
        normalizeId(deviceId) === normalizeId(session?.creatorDeviceId || deviceId)
            ? 'CREATOR'
            : 'PARTNER'
    );

    if (!session) {
        const cleanName = sanitizedDeviceName.replace(/[^a-zA-Z0-9]/g, '');
        const initialDbName = cleanName ? `session_${cleanName}` : `session_${code}`;

        await Session.create({
            connectionCode: code,
            dbName: initialDbName,
            creatorDeviceId: deviceId,
            relationshipType: relationshipType || 'ROMANTIC',
            participants: [{
                clientId: client.id,
                deviceId,
                deviceName: sanitizedDeviceName,
                partnerLabel: sanitizedPartnerLabel,
                memberId,
                role: normalizedRole,
                connectedAt: new Date()
            }],
            events: [{ type: 'created', timestamp: new Date(), data: { deviceId, deviceName: sanitizedDeviceName, partnerLabel: sanitizedPartnerLabel } }]
        });
        return;
    }

    if (relationshipType) {
        session.relationshipType = relationshipType;
    }

    const participantIndex = session.participants.findIndex(p => p.deviceId === deviceId);
    let updatedParticipants = [...session.participants];

    if (participantIndex !== -1) {
        updatedParticipants[participantIndex].clientId = client.id;
        updatedParticipants[participantIndex].deviceName = sanitizedDeviceName;
        updatedParticipants[participantIndex].memberId = memberId || updatedParticipants[participantIndex].memberId || null;
        updatedParticipants[participantIndex].role = normalizedRole;
        updatedParticipants[participantIndex].partnerLabel = sanitizedPartnerLabel || updatedParticipants[participantIndex].partnerLabel || null;
        updatedParticipants[participantIndex].connectedAt = new Date();
    } else {
        updatedParticipants.push({
            clientId: client.id,
            deviceId,
            deviceName: sanitizedDeviceName,
            partnerLabel: sanitizedPartnerLabel,
            memberId,
            role: normalizedRole,
            connectedAt: new Date()
        });
    }

    // Generate friendly DB Name based on all known partners
    const names = updatedParticipants
        .map(p => p.deviceName.replace(/[^a-zA-Z0-9]/g, ''))
        .filter(n => n.length > 0)
        .join('_and_');
    const newDbName = names ? `session_${names}` : `session_${code}`;

    if (participantIndex !== -1) {
        await Session.updateOne({ _id: session._id }, {
            $set: {
                participants: updatedParticipants,
                dbName: newDbName
            },
            $push: { events: { $each: [{ type: 'rejoined', timestamp: new Date(), data: { deviceId, deviceName: sanitizedDeviceName, partnerLabel: sanitizedPartnerLabel } }], $slice: -100 } }
        });
    } else {
        await Session.updateOne({ _id: session._id }, {
            $set: {
                participants: updatedParticipants,
                dbName: newDbName
            },
            $push: { events: { $each: [{ type: 'joined', timestamp: new Date(), data: { deviceId, deviceName: sanitizedDeviceName, partnerLabel: sanitizedPartnerLabel } }], $slice: -100 } }
        });
    }

    // Proactively initialize or refresh the connection models if names have been fully resolved
    if (updatedParticipants.length >= 2) {
        await getPairModels(connectionCode);
    }
}

function normalizeId(value) {
    return typeof value === 'string' ? value.toLowerCase() : '';
}

function normalizePhoneNumber(value) {
    const raw = String(value || '').trim();
    if (!raw) return '';
    const digits = raw.replace(/\D/g, '');
    if (digits.length < 10 || digits.length > 15) return '';
    return raw.startsWith('+') ? `+${digits}` : `+${digits}`;
}

// Session tokens come in two flavours: our own opaque one (32 chars, see
// issueAuthSession) and a Firebase ID token, which is a ~1 KB JWT. The old 256-char
// cap silently truncated the latter into something that verified as neither, so every
// request authenticated with a Firebase token was rejected as "Session expired".
const SESSION_TOKEN_MAX = 4096;

function sanitizeText(value, maxLength = 160) {
    return String(value || '').trim().slice(0, maxLength);
}

function normalizeGender(value) {
    const normalized = sanitizeText(value || '', 12).toLowerCase();
    return PROFILE_GENDER_VALUES.has(normalized) ? normalized : null;
}

function isProfileComplete(member) {
    return Boolean(
        sanitizeText(member?.displayName || '', 80) &&
        normalizeGender(member?.gender)
    );
}

function decodeBase64Payload(value) {
    const raw = String(value || '').trim();
    if (!raw) return null;
    const data = raw.includes(',') ? raw.split(',').pop() : raw;
    try {
        return Buffer.from(data, 'base64');
    } catch (_error) {
        return null;
    }
}

function mimeToExtension(mimeType) {
    switch (String(mimeType || '').toLowerCase()) {
        case 'image/png':
            return '.png';
        case 'image/webp':
            return '.webp';
        case 'image/gif':
            return '.gif';
        case 'image/jpeg':
        case 'image/jpg':
        default:
            return '.jpg';
    }
}

function hashSecret(value) {
    return crypto.createHash('sha256').update(String(value || ''), 'utf8').digest('hex');
}

function generateSessionToken() {
    return crypto.randomBytes(24).toString('base64url');
}

function generateRestoreToken() {
    return generateSessionToken();
}

function generateOtpCode() {
    return String(Math.floor(100000 + Math.random() * 900000));
}

function maskOtpCode(otpCode) {
    const clean = String(otpCode || '').replace(/\D/g, '');
    const suffix = clean.slice(-2);
    return suffix ? `••••${suffix}` : null;
}

function normalizeRecoveryCode(value) {
    return String(value || '')
        .trim()
        .toUpperCase()
        .replace(/[^A-Z0-9]/g, '');
}

function generateRecoveryCode() {
    const raw = normalizeRecoveryCode(crypto.randomBytes(6).toString('hex'));
    return `GIGI-${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}`;
}

function maskRecoveryCode(recoveryCode) {
    const normalized = normalizeRecoveryCode(recoveryCode);
    const suffix = normalized.slice(-4);
    return suffix ? `••••-${suffix}` : null;
}

function normalizeRole(role, fallback = 'PARTNER') {
    return role === 'CREATOR' || role === 'PARTNER' ? role : fallback;
}

function hasPhoneAuth(member) {
    return Boolean(sanitizeText(member?.phoneNumber || '', 32));
}

function hasGoogleAuth(member) {
    return Boolean(sanitizeText(member?.googleEmail || '', 100));
}

/** True when the member has authenticated via phone OTP or Google Sign-In */
function hasValidAuth(member) {
    return hasPhoneAuth(member) || hasGoogleAuth(member);
}

async function resolveMemberBySessionToken(sessionToken) {
    if (!sessionToken) return null;

    // 1. Try Firebase Token Verification first
    try {
        const decodedToken = await admin.auth().verifyIdToken(sessionToken);
        if (decodedToken && decodedToken.uid) {
            const email = (decodedToken.email || '').toLowerCase().trim();
            const queryConditions = [{ memberId: decodedToken.uid }];
            if (decodedToken.phone_number) queryConditions.push({ phoneNumber: decodedToken.phone_number });
            if (email) queryConditions.push({ googleEmail: email });

            let member = await Member.findOne({
                $or: queryConditions,
                revokedAt: null
            });

            if (member) {
                // Ensure UID is set as memberId if we found by phone or email
                if (!member.memberId || member.memberId !== decodedToken.uid) {
                    member.memberId = decodedToken.uid;
                    await member.save();
                }
                return member;
            }
        }
    } catch (e) {
        // Not a valid firebase token, or expired. 
        // Fallback to legacy session check
    }

    // 2. Legacy Session Check
    const session = await AuthSession.findOne({
        sessionTokenHash: hashSecret(sessionToken),
        revokedAt: null,
        expiresAt: { $gt: new Date() }
    });
    if (!session?.memberId) return null;

    session.lastSeenAt = new Date();
    await session.save();
    return Member.findOne({ memberId: session.memberId, revokedAt: null });
}

async function revokeAuthSessions(memberId, deviceId = null) {
    if (!memberId) return;
    const filter = {
        memberId,
        revokedAt: null
    };
    if (deviceId) {
        filter.deviceId = normalizeId(deviceId);
    }
    await AuthSession.updateMany(filter, { $set: { revokedAt: new Date() } });
}

/**
 * Keeps a device's session list from growing without bound, without ever touching the
 * tokens still in active use. Only sessions well past their usefulness are dropped —
 * the newest few always survive so a client holding a slightly older token keeps working.
 */
const KEEP_SESSIONS_PER_DEVICE = 5;
async function pruneOldAuthSessions(memberId, deviceId) {
    if (!memberId || !isMongoReady(gigiConn)) return;
    try {
        const filter = { memberId, revokedAt: null };
        if (deviceId) filter.deviceId = deviceId;
        const stale = await AuthSession.find(filter)
            .sort({ createdAt: -1 })
            .skip(KEEP_SESSIONS_PER_DEVICE)
            .select('_id')
            .lean();
        if (stale.length) {
            await AuthSession.deleteMany({ _id: { $in: stale.map(s => s._id) } });
        }
    } catch (e) {
        logEvent('auth.session_prune_failed', { memberId, error: e.message });
    }
}

async function issueAuthSession(member, { deviceId, deviceName } = {}) {
    if (!member?.memberId) return null;
    const token = generateSessionToken();
    const normalizedDeviceId = normalizeId(deviceId);

    // Deliberately NOT revoking the device's previous sessions here.
    //
    // This used to revoke every earlier session for the same device before minting the
    // new one, which meant each bootstrap invalidated the token the app was still
    // holding. Any in-flight request, socket reconnect or component that had already
    // read `authToken` then got a 401 and surfaced "Session expired. Please sign in
    // again." to a perfectly signed-in user. The database showed the damage plainly:
    // 296 sessions for one member, 295 of them revoked, several within the same second.
    //
    // Old tokens now simply age out via AUTH_SESSION_TTL_MS (90 days). Revocation
    // stays available through revokeAuthSessions() for an explicit sign-out or a
    // security event — which is what it was always meant for.
    await pruneOldAuthSessions(member.memberId, normalizedDeviceId);

    await AuthSession.create({
        memberId: member.memberId,
        sessionTokenHash: hashSecret(token),
        deviceId: normalizedDeviceId || null,
        deviceName: sanitizeText(deviceName || member.deviceName || 'Unknown device', 80),
        expiresAt: new Date(Date.now() + AUTH_SESSION_TTL_MS),
        lastSeenAt: new Date()
    });

    return token;
}

async function issueOtpChallenge(phoneNumber, deviceId, deviceName) {
    const normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
    if (!normalizedPhoneNumber) {
        return null;
    }

    const otpCode = generateOtpCode();
    await AuthOtp.updateMany(
        {
            phoneNumber: normalizedPhoneNumber,
            consumedAt: null,
            expiresAt: { $gt: new Date() }
        },
        {
            $set: {
                consumedAt: new Date()
            }
        }
    );

    await AuthOtp.create({
        phoneNumber: normalizedPhoneNumber,
        deviceId: normalizeId(deviceId),
        deviceName: sanitizeText(deviceName || 'Unknown device', 80),
        codeHash: hashSecret(otpCode),
        codePreview: OTP_DEV_MODE ? otpCode : maskOtpCode(otpCode),
        expiresAt: new Date(Date.now() + OTP_TTL_MS)
    });

    logEvent('auth.otp_issued', {
        phoneNumber: normalizedPhoneNumber,
        deviceId: normalizeId(deviceId),
        devMode: OTP_DEV_MODE
    });

    return {
        phoneNumber: normalizedPhoneNumber,
        otpCode
    };
}

async function verifyOtpChallenge(phoneNumber, otpCode) {
    const normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
    const normalizedOtpCode = String(otpCode || '').replace(/\D/g, '');
    if (!normalizedPhoneNumber || normalizedOtpCode.length !== 6) {
        return { valid: false, reason: 'Invalid phone number or OTP' };
    }

    const challenge = await AuthOtp.findOne({
        phoneNumber: normalizedPhoneNumber,
        consumedAt: null,
        expiresAt: { $gt: new Date() }
    }).sort({ createdAt: -1 });

    if (!challenge) {
        return { valid: false, reason: 'OTP expired. Request a new code.' };
    }

    challenge.attempts = (challenge.attempts || 0) + 1;
    const otpMatches = hashSecret(normalizedOtpCode) === challenge.codeHash;
    if (!otpMatches) {
        await challenge.save();
        return { valid: false, reason: 'Incorrect OTP. Please try again.' };
    }

    challenge.consumedAt = new Date();
    await challenge.save();
    return { valid: true, phoneNumber: normalizedPhoneNumber };
}

function normalizeServerState(doc) {
    const state = {
        mode: doc?.mode === 'MAINTENANCE' ? 'MAINTENANCE' : 'ONLINE',
        message: sanitizeText(doc?.message || '', 240),
        updatedAt: doc?.updatedAt || new Date()
    };
    cachedServerState = state;
    return state;
}

function buildServerStatusPayload(state = cachedServerState) {
    return {
        mode: state?.mode === 'MAINTENANCE' ? 'MAINTENANCE' : 'ONLINE',
        message: sanitizeText(state?.message || '', 240),
        updatedAt: new Date(state?.updatedAt || Date.now()).toISOString(),
        serverTime: Date.now()
    };
}

function isMaintenanceMode(state = cachedServerState) {
    return state?.mode === 'MAINTENANCE';
}

async function getOrCreateServerStateDocument() {
    const doc = await ServerState.findOneAndUpdate(
        { singletonKey: SERVER_STATE_KEY },
        {
            $setOnInsert: {
                singletonKey: SERVER_STATE_KEY,
                mode: 'ONLINE',
                message: '',
                updatedBy: 'system'
            }
        },
        { upsert: true, new: true, setDefaultsOnInsert: true }
    );
    return normalizeServerState(doc);
}

async function readServerState() {
    if (!isMongoReady(gigiConn)) {
        return cachedServerState;
    }
    try {
        return await getOrCreateServerStateDocument();
    } catch (error) {
        logEvent('server_state.read_failed', { error: error.message });
        return cachedServerState;
    }
}

async function setServerState(mode, message, updatedBy = 'system') {
    const doc = await ServerState.findOneAndUpdate(
        { singletonKey: SERVER_STATE_KEY },
        {
            $set: {
                mode: mode === 'MAINTENANCE' ? 'MAINTENANCE' : 'ONLINE',
                message: sanitizeText(message || '', 240),
                updatedBy: sanitizeText(updatedBy || 'system', 80)
            },
            $setOnInsert: {
                singletonKey: SERVER_STATE_KEY
            }
        },
        { upsert: true, new: true, setDefaultsOnInsert: true }
    );
    return normalizeServerState(doc);
}

function sendServerStatus(ws, { messageId = uuidv4(), closeAfter = false } = {}) {
    const payload = buildServerStatusPayload(cachedServerState);
    sendToClient(ws, {
        connectionId: null,
        senderDeviceId: 'server',
        actionType: 'server_status',
        payload,
        messageId,
        legacyType: 'server_status',
        legacyPayload: payload
    });

    if (closeAfter) {
        setTimeout(() => {
            if (ws.readyState === WebSocket.OPEN) {
                ws.close(4002, cachedServerState.message || 'Server maintenance');
            }
        }, MAINTENANCE_CLOSE_DELAY_MS);
    }
}

async function broadcastServerStatus({ closeClients = false } = {}) {
    const state = await readServerState();
    logEvent('server_state.broadcast', {
        mode: state.mode,
        closeClients
    });
    wss.clients.forEach(ws => {
        const client = clients.get(ws);
        if (!client || ws.readyState !== WebSocket.OPEN) return;
        sendServerStatus(ws, { closeAfter: closeClients });
    });
}

async function rejectMaintenanceHttp(res) {
    const state = await readServerState();
    if (!isMaintenanceMode(state)) return false;

    return res.status(503).json({
        error: state.message || 'Server is in maintenance',
        code: 'SERVER_MAINTENANCE',
        mode: state.mode,
        message: state.message || '',
        serverTime: Date.now()
    });
}

async function rejectMaintenanceWs(ws, normalizedMessage, connectionId = null) {
    const state = await readServerState();
    if (!isMaintenanceMode(state)) return false;

    const payload = {
        ...buildServerStatusPayload(state),
        code: 'SERVER_MAINTENANCE'
    };
    sendToClient(ws, {
        connectionId: normalizedMessage?.connectionId || connectionId || null,
        senderDeviceId: 'server',
        actionType: 'server_status',
        payload,
        messageId: normalizedMessage?.messageId || uuidv4(),
        legacyType: 'error',
        legacyPayload: {
            message: state.message || 'Server is in maintenance',
            code: 'SERVER_MAINTENANCE'
        }
    });
    return true;
}

async function resolveMemberByRestoreToken(restoreToken) {
    if (!restoreToken) return null;
    return Member.findOne({
        restoreTokenHash: hashSecret(restoreToken),
        revokedAt: null
    });
}

async function resolveMemberByRecoveryCode(recoveryCode) {
    const normalizedRecoveryCode = normalizeRecoveryCode(recoveryCode);
    if (!normalizedRecoveryCode) return null;
    return Member.findOne({
        recoveryCodeHash: hashSecret(normalizedRecoveryCode),
        revokedAt: null
    });
}

async function resolveSingleMemberByDevice(deviceId) {
    const normalizedDeviceId = normalizeId(deviceId);
    if (!normalizedDeviceId) {
        return { member: null, ambiguous: false };
    }

    const matches = await Member.find({
        revokedAt: null,
        $or: [
            { primaryDeviceId: normalizedDeviceId },
            { knownDeviceIds: normalizedDeviceId }
        ]
    })
        .sort({ updatedAt: -1, lastSeenAt: -1 })
        .limit(5);

    if (matches.length === 0) {
        return { member: null, ambiguous: false };
    }

    return { member: matches[0], ambiguous: matches.length > 1 };
}

async function attachDeviceToMember(member, deviceId, deviceName) {
    if (!member || !deviceId) return member;

    const normalizedDeviceId = normalizeId(deviceId);
    const knownDeviceIds = new Set(
        Array.isArray(member.knownDeviceIds)
            ? member.knownDeviceIds.map(normalizeId).filter(Boolean)
            : []
    );
    knownDeviceIds.add(normalizedDeviceId);

    member.primaryDeviceId = member.primaryDeviceId || normalizedDeviceId;
    member.knownDeviceIds = Array.from(knownDeviceIds);
    member.deviceName = sanitizeText(deviceName || member.deviceName || 'Unknown device', 80);
    member.lastSeenAt = new Date();
    await member.save();
    return member;
}

async function createMemberForDevice(deviceId, deviceName) {
    const recoveryCode = generateRecoveryCode();
    const restoreToken = generateRestoreToken();
    const member = await Member.create({
        memberId: uuidv4(),
        primaryDeviceId: normalizeId(deviceId),
        knownDeviceIds: [normalizeId(deviceId)],
        restoreTokenHash: hashSecret(restoreToken),
        recoveryCodeHash: hashSecret(normalizeRecoveryCode(recoveryCode)),
        recoveryCodeMasked: maskRecoveryCode(recoveryCode),
        deviceName: sanitizeText(deviceName || 'Unknown device', 80),
        lastSeenAt: new Date()
    });

    member._plainRestoreToken = restoreToken;
    member._plainRecoveryCode = recoveryCode;
    return member;
}

async function resolveOrCreateMember({ deviceId, deviceName, sessionToken = null, restoreToken = null, allowCreate = true }) {
    const bySession = await resolveMemberBySessionToken(sessionToken);
    if (bySession) {
        await attachDeviceToMember(bySession, deviceId, deviceName);
        return { member: bySession, ambiguous: false, created: false };
    }

    const byToken = await resolveMemberByRestoreToken(restoreToken);
    if (byToken) {
        await attachDeviceToMember(byToken, deviceId, deviceName);
        return { member: byToken, ambiguous: false, created: false };
    }

    const byDevice = await resolveSingleMemberByDevice(deviceId);
    if (byDevice.member) {
        await attachDeviceToMember(byDevice.member, deviceId, deviceName);
        return { member: byDevice.member, ambiguous: false, created: false };
    }

    if (!allowCreate || byDevice.ambiguous) {
        return { member: null, ambiguous: byDevice.ambiguous, created: false };
    }

    const createdMember = await createMemberForDevice(deviceId, deviceName);
    return { member: createdMember, ambiguous: false, created: true };
}

async function rotateRestoreToken(member) {
    const restoreToken = generateRestoreToken();
    member.restoreTokenHash = hashSecret(restoreToken);
    member.lastSeenAt = new Date();
    await member.save();
    return restoreToken;
}

function buildMemberIdentityPayload(member, authToken) {
    if (!member || !hasValidAuth(member)) return null;
    return {
        memberId: member.memberId,
        authToken,
        phoneNumber: member.phoneNumber || null,
        googleEmail: member.googleEmail || null,
        displayName: member.displayName || member.googleDisplayName || null,
        gender: normalizeGender(member.gender),
        avatarUrl: sanitizeText(member.avatarUrl || '', 240) || null,
        profileEmojiUrl: sanitizeText(member.profileEmojiUrl || '', 512) || null,
        avatarMode: member.avatarMode === 'TWIGI' ? 'TWIGI' : 'EMOJI',
        twigiConfig: (member.twigiConfig && typeof member.twigiConfig === 'object') ? member.twigiConfig : null,
        twigiRenderUrl: sanitizeText(member.twigiRenderUrl || '', 512) || null,
        prefsBlob: (member.prefsBlob && typeof member.prefsBlob === 'object') ? member.prefsBlob : {},
        themeSongTitle: sanitizeText(member.themeSongTitle || '', 120) || null,
        themeSongUrl: sanitizeText(member.themeSongUrl || '', 512) || null,
        discoverable: Boolean(member.discoverable),
        handle: member.handle || null,
        bio: member.bio || null,
        nebulaSeed: typeof member.nebulaSeed === 'number' ? member.nebulaSeed : 42,
        profileComplete: isProfileComplete(member)
    };
}

function serializeSharedAlarm(alarm) {
    if (!alarm?.alarmId || !alarm?.connectionCode) return null;
    return {
        alarmId: alarm.alarmId,
        connectionCode: String(alarm.connectionCode || '').toLowerCase(),
        title: sanitizeText(alarm.title || 'Shared alarm', 120) || 'Shared alarm',
        note: sanitizeText(alarm.note || '', 320) || null,
        dueAt: toTimestampMs(alarm.dueAt),
        recurrencePattern: sanitizeText(alarm.recurrencePattern || '', 40) || null,
        customIntervalMillis: Number.isFinite(alarm.customIntervalMillis) ? alarm.customIntervalMillis : null,
        repeatStartHour: Number.isFinite(alarm.repeatStartHour) ? alarm.repeatStartHour : null,
        repeatStartMinute: Number.isFinite(alarm.repeatStartMinute) ? alarm.repeatStartMinute : null,
        repeatEndHour: Number.isFinite(alarm.repeatEndHour) ? alarm.repeatEndHour : null,
        repeatEndMinute: Number.isFinite(alarm.repeatEndMinute) ? alarm.repeatEndMinute : null,
        ownerMemberId: alarm.ownerMemberId || null,
        ownerDisplayName: sanitizeText(alarm.ownerDisplayName || '', 80) || null,
        isActive: alarm.isActive !== false,
        updatedAt: toTimestampMs(alarm.updatedAt || alarm.createdAt || Date.now())
    };
}

function serializeLoveCardStack(stack, requesterMemberId = null) {
    if (!stack?.stackId || !stack?.connectionCode) return null;
    return {
        stackId: stack.stackId,
        connectionCode: String(stack.connectionCode || '').toLowerCase(),
        title: sanitizeText(stack.title || 'A sweet little deck', 120) || 'A sweet little deck',
        senderMemberId: stack.senderMemberId || null,
        senderDisplayName: sanitizeText(stack.senderDisplayName || '', 80) || null,
        recipientMemberId: stack.recipientMemberId || null,
        status: sanitizeText(stack.status || 'SENT', 24) || 'SENT',
        theme: sanitizeText(stack.theme || '', 40) || null,
        previewText: sanitizeText(stack.previewText || '', 240) || null,
        isIncoming: requesterMemberId ? stack.senderMemberId !== requesterMemberId : false,
        createdAt: toTimestampMs(stack.createdAt),
        updatedAt: toTimestampMs(stack.updatedAt || stack.createdAt),
        openedAt: stack.openedAt ? toTimestampMs(stack.openedAt) : null,
        answeredAt: stack.answeredAt ? toTimestampMs(stack.answeredAt) : null
    };
}

function serializeLoveCardItem(item) {
    if (!item?.cardId || !item?.stackId || !item?.connectionCode) return null;
    let decorations = [];
    if (item.decorationsJson) {
        try {
            const parsed = JSON.parse(item.decorationsJson);
            decorations = Array.isArray(parsed) ? parsed : [];
        } catch (_error) {
            decorations = [];
        }
    }
    return {
        cardId: item.cardId,
        stackId: item.stackId,
        connectionCode: String(item.connectionCode || '').toLowerCase(),
        type: sanitizeText(item.type || 'CUTE_NOTE', 40) || 'CUTE_NOTE',
        prompt: sanitizeText(item.prompt || 'A little note for you', 240) || 'A little note for you',
        choices: Array.isArray(item.choices) ? item.choices.map(choice => sanitizeText(choice, 80)).filter(Boolean) : [],
        theme: sanitizeText(item.theme || '', 40) || null,
        animationStyle: sanitizeText(item.animationStyle || '', 40) || null,
        decorations,
        decorationsJson: item.decorationsJson || null,
        sortOrder: Number.isFinite(item.sortOrder) ? item.sortOrder : 0
    };
}

function serializeLoveCardResponse(response) {
    if (!response?.stackId || !response?.cardId) return null;
    return {
        responseId: response.responseId || `${response.stackId}:${response.cardId}`,
        stackId: response.stackId,
        cardId: response.cardId,
        answerText: sanitizeText(response.answerText || '', 320) || null,
        selectedChoice: sanitizeText(response.selectedChoice || '', 120) || null,
        emojiReaction: sanitizeText(response.emojiReaction || '', 24) || null,
        answeredAt: toTimestampMs(response.answeredAt || response.createdAt || Date.now()),
        answeredByMemberId: response.answeredByMemberId || null
    };
}

async function buildBootstrapSharedAlarms(member) {
    if (!member?.memberId) return [];
    const memberships = await ConnectionMembership.find({
        memberId: member.memberId,
        archivedAt: null
    }).lean();
    const codes = [...new Set(memberships.map(item => String(item.connectionCode || '').toLowerCase()).filter(Boolean))];
    if (codes.length === 0) return [];
    const alarms = await SharedAlarm.find({
        connectionCode: { $in: codes },
        isActive: true,
        deletedAt: null
    }).sort({ dueAt: 1, updatedAt: -1 }).lean();
    return alarms.map(serializeSharedAlarm).filter(Boolean);
}

async function buildBootstrapLoveCards(member) {
    if (!member?.memberId) {
        return { stacks: [], items: [], responses: [] };
    }
    const memberships = await ConnectionMembership.find({
        memberId: member.memberId,
        archivedAt: null
    }).lean();
    const codes = [...new Set(memberships.map(item => String(item.connectionCode || '').toLowerCase()).filter(Boolean))];
    if (codes.length === 0) {
        return { stacks: [], items: [], responses: [] };
    }

    const stacks = await LoveCardStack.find({
        connectionCode: { $in: codes }
    }).sort({ updatedAt: -1 }).limit(60).lean();
    const stackIds = stacks.map(stack => stack.stackId).filter(Boolean);
    if (stackIds.length === 0) {
        return { stacks: [], items: [], responses: [] };
    }
    const items = await LoveCardItem.find({
        stackId: { $in: stackIds }
    }).sort({ sortOrder: 1, createdAt: 1 }).lean();
    const responses = await LoveCardResponse.find({
        stackId: { $in: stackIds }
    }).sort({ answeredAt: 1, createdAt: 1 }).lean();

    return {
        stacks: stacks.map(stack => serializeLoveCardStack(stack, member.memberId)).filter(Boolean),
        items: items.map(serializeLoveCardItem).filter(Boolean),
        responses: responses.map(serializeLoveCardResponse).filter(Boolean)
    };
}

function sanitizeLoveCardType(value) {
    const normalized = String(value || '').trim().toUpperCase();
    return ['CUTE_NOTE', 'QUESTION', 'MULTIPLE_CHOICE', 'ANIMATED_GIFT'].includes(normalized)
        ? normalized
        : 'CUTE_NOTE';
}

function sanitizeLoveCardDecorations(rawDecorations) {
    const source = Array.isArray(rawDecorations) ? rawDecorations : [];
    const normalized = source
        .slice(0, 8)
        .map((entry, index) => {
            const content = sanitizeText(entry?.content || '', 32);
            if (!content) return null;
            return {
                id: sanitizeText(entry?.id || uuidv4(), 120) || `${uuidv4()}_${index}`,
                content,
                x: Math.max(0.05, Math.min(0.88, Number(entry?.x ?? 0.5) || 0.5)),
                y: Math.max(0.05, Math.min(0.88, Number(entry?.y ?? 0.32) || 0.32)),
                scale: Math.max(0.7, Math.min(1.6, Number(entry?.scale ?? 1) || 1)),
                rotation: Math.max(-30, Math.min(30, Number(entry?.rotation ?? 0) || 0)),
                style: sanitizeText(entry?.style || 'emoji', 20) || 'emoji'
            };
        })
        .filter(Boolean);

    return normalized.length > 0 ? JSON.stringify(normalized) : null;
}

function sanitizeLoveCardItems(rawCards, stackId, connectionCode) {
    const source = Array.isArray(rawCards) ? rawCards : [];
    return source
        .slice(0, 12)
        .map((card, index) => {
            const cardId = sanitizeText(card?.cardId || uuidv4(), 120);
            const prompt = sanitizeText(card?.prompt || '', 240) || 'A little note for you';
            if (!cardId) return null;
            const rawChoices = Array.isArray(card?.choices) ? card.choices : [];
            return {
                cardId,
                stackId,
                connectionCode,
                type: sanitizeLoveCardType(card?.type),
                prompt,
                choices: rawChoices
                    .slice(0, 4)
                    .map(choice => sanitizeText(choice, 80))
                    .filter(Boolean),
                theme: sanitizeText(card?.theme || '', 40) || null,
                animationStyle: sanitizeText(card?.animationStyle || '', 40) || null,
                decorationsJson: sanitizeLoveCardDecorations(card?.decorations),
                sortOrder: index
            };
        })
        .filter(Boolean);
}

function sanitizeLoveCardResponses(rawResponses, stackId, answeredByMemberId = null) {
    const source = Array.isArray(rawResponses) ? rawResponses : [];
    return source
        .map(entry => {
            const cardId = sanitizeText(entry?.cardId || '', 120);
            if (!cardId) return null;
            const answerText = sanitizeText(entry?.answerText || '', 320) || null;
            const selectedChoice = sanitizeText(entry?.selectedChoice || '', 120) || null;
            const emojiReaction = sanitizeText(entry?.emojiReaction || '', 24) || null;
            if (!answerText && !selectedChoice && !emojiReaction) return null;
            return {
                responseId: sanitizeText(entry?.responseId || `${stackId}:${cardId}`, 180) || `${stackId}:${cardId}`,
                stackId,
                cardId,
                answerText,
                selectedChoice,
                emojiReaction,
                answeredAt: entry?.answeredAt ? new Date(entry.answeredAt) : new Date(),
                answeredByMemberId
            };
        })
        .filter(Boolean);
}

async function upsertConnectionMembership({
    memberId,
    connectionCode,
    role,
    partnerDisplayNameCache = null,
    archivedAt = null
}) {
    if (!memberId || !connectionCode) return null;
    const code = connectionCode.toLowerCase();

    return ConnectionMembership.findOneAndUpdate(
        { memberId, connectionCode: code },
        {
            $set: {
                role: normalizeRole(role),
                partnerDisplayNameCache: sanitizeText(partnerDisplayNameCache || '', 80) || null,
                archivedAt,
                lastConnectedAt: new Date()
            },
            $setOnInsert: {
                memberId,
                connectionCode: code
            }
        },
        { upsert: true, new: true }
    );
}

async function archiveConnectionMembership(memberId, connectionCode) {
    if (!memberId || !connectionCode) return null;
    return ConnectionMembership.findOneAndUpdate(
        { memberId, connectionCode: connectionCode.toLowerCase() },
        {
            $set: {
                archivedAt: new Date(),
                lastConnectedAt: new Date()
            }
        },
        { new: true }
    );
}

function getParticipantRecord(session, requesterMemberId, requesterDeviceId) {
    const participants = session?.participants || [];
    const normalizedRequesterMemberId = requesterMemberId || null;
    const normalizedRequesterDeviceId = normalizeId(requesterDeviceId);

    return participants.find(participant => (
        (normalizedRequesterMemberId && participant.memberId === normalizedRequesterMemberId) ||
        normalizeId(participant.deviceId) === normalizedRequesterDeviceId
    )) || null;
}

function getPartnerParticipant(session, requesterMemberId, requesterDeviceId) {
    const requester = getParticipantRecord(session, requesterMemberId, requesterDeviceId);
    if (!requester) return null;

    return (session?.participants || []).find(participant => (
        participant !== requester &&
        (
            requester.memberId
                ? participant.memberId !== requester.memberId
                : normalizeId(participant.deviceId) !== normalizeId(requester.deviceId)
        )
    )) || null;
}

function resolvePartnerLabelFromSession(session, requesterMemberId, requesterDeviceId, fallbackName = 'Partner') {
    const requester = getParticipantRecord(session, requesterMemberId, requesterDeviceId);
    const partner = getPartnerParticipant(session, requesterMemberId, requesterDeviceId);
    return sanitizeText(
        requester?.partnerLabel
        || partner?.deviceName
        || fallbackName,
        80
    );
}

async function buildBootstrapConnections(member, requesterDeviceId) {
    if (!member?.memberId) return [];

    const memberships = await ConnectionMembership.find({
        memberId: member.memberId,
        archivedAt: null
    }).sort({ updatedAt: -1 }).lean();

    const connectionsForBootstrap = [];
    for (const membership of memberships) {
        const connectionCode = membership.connectionCode?.toLowerCase();
        if (!connectionCode) continue;

        const session = await Session.findOne({ connectionCode }).lean();
        if (!session) continue;

        const presence = await resolvePartnerPresence(connectionCode, requesterDeviceId);
        const requesterId = normalizeId(requesterDeviceId);
        const requesterParticipant = getParticipantRecord(session, membership.memberId, requesterDeviceId);
        const partnerParticipant = getPartnerParticipant(session, membership.memberId, requesterDeviceId);
        const partnerMember = partnerParticipant?.memberId
            ? await Member.findOne({ memberId: partnerParticipant.memberId, revokedAt: null }).lean()
            : null;
        const derivedRole = normalizeId(session.creatorDeviceId) === requesterId
            ? 'CREATOR'
            : requesterParticipant?.role
            || membership.role
            || 'PARTNER';
        const normalizedRole = normalizeRole(derivedRole);
        const resolvedPartnerName = sanitizeText(
            membership.partnerDisplayNameCache
            || requesterParticipant?.partnerLabel
            || presence.partnerDeviceName
            || partnerParticipant?.deviceName
            || 'Partner',
            80
        );

        if (membership.role !== normalizedRole || membership.partnerDisplayNameCache !== resolvedPartnerName) {
            await upsertConnectionMembership({
                memberId: membership.memberId,
                connectionCode,
                role: normalizedRole,
                partnerDisplayNameCache: resolvedPartnerName
            });
        }

        // Full member roster (for group "Sweet Corner" cards), enriched with profiles.
        const members = await Promise.all((session.participants || []).map(async (p) => {
            const pm = p.memberId ? await Member.findOne({ memberId: p.memberId, revokedAt: null }).lean() : null;
            return {
                deviceId: p.deviceId || null,
                memberId: p.memberId || null,
                name: sanitizeText(pm?.displayName || p.partnerLabel || p.deviceName || 'Member', 80),
                avatarUrl: sanitizeText(pm?.avatarUrl || '', 240) || null,
                // Twigi-first: when a member uses their Twigi, every roster/moon shows it
                emojiUrl: (pm?.avatarMode === 'TWIGI' && pm?.twigiRenderUrl)
                    ? (sanitizeText(pm.twigiRenderUrl, 512) || null)
                    : (sanitizeText(pm?.profileEmojiUrl || '', 512) || null),
                role: normalizeRole(p.role || 'PARTNER'),
                isSelf: normalizeId(p.deviceId) === requesterId
            };
        }));

        connectionsForBootstrap.push({
            connectionCode,
            connectionId: connectionCode,
            role: normalizedRole,
            relationshipType: session.relationshipType || 'ROMANTIC',
            isGroup: (session.relationshipType || '').toUpperCase() === 'GROUP',
            partnerDisplayName: resolvedPartnerName,
            partnerDeviceId: presence.partnerDeviceId || partnerParticipant?.deviceId || null,
            partnerAvatarUrl: sanitizeText(partnerMember?.avatarUrl || '', 240) || null,
            // For groups this carries the GROUP's shared emoji; for 1-1 it's the partner's.
            partnerEmojiUrl: (session.relationshipType || '').toUpperCase() === 'GROUP'
                ? (sanitizeText(session.groupEmojiUrl || '', 512) || null)
                : (sanitizeText(partnerMember?.profileEmojiUrl || '', 512) || null),
            partnerEmoji: partnerMember?.emoji || '🌻',
            // Twigi identity — 1-1 only (groups keep their shared emoji).
            partnerAvatarMode: partnerMember?.avatarMode === 'TWIGI' ? 'TWIGI' : 'EMOJI',
            partnerTwigiUrl: sanitizeText(partnerMember?.twigiRenderUrl || '', 512) || null,
            creatorDeviceId: session.creatorDeviceId || null,
            members,
            isArchived: false,
            origin: membership.origin || 'INVITE',
            trustRing: typeof membership.trustRing === 'number' ? membership.trustRing : 0,
            lastSeenAt: presence.lastSeenAt || null,
            transportHint: presence.isOnline ? 'CONNECTED' : 'CONNECTING',
            partnerPresence: presence.isOnline ? 'ONLINE' : 'OFFLINE'
        });
    }

    return connectionsForBootstrap;
}

async function buildBootstrapResponse({ member = null, deviceId, deviceName, ambiguous = false }) {
    const serverState = await readServerState();
    let memberIdentity = null;
    let connectionsForBootstrap = [];
    let sharedAlarms = [];
    let loveCards = { stacks: [], items: [], responses: [] };
    const authenticatedMember = hasValidAuth(member) ? member : null;

    if (authenticatedMember) {
        await attachDeviceToMember(authenticatedMember, deviceId, deviceName);
        const authToken = await issueAuthSession(authenticatedMember, { deviceId, deviceName });
        memberIdentity = buildMemberIdentityPayload(authenticatedMember, authToken);
        connectionsForBootstrap = await buildBootstrapConnections(authenticatedMember, deviceId);
        sharedAlarms = await buildBootstrapSharedAlarms(authenticatedMember);
        loveCards = await buildBootstrapLoveCards(authenticatedMember);
    }

    // appConfig carries the member's resolved plan/limits so the client can gate features.
    // For unauthenticated callers this resolves to the default free plan.
    const appConfig = await buildAppConfig(authenticatedMember);

    return {
        serverMode: serverState.mode,
        maintenanceMessage: serverState.message || '',
        memberIdentity,
        appConfig,
        connections: connectionsForBootstrap,
        sharedAlarms,
        loveCardStacks: loveCards.stacks,
        loveCardItems: loveCards.items,
        loveCardResponses: loveCards.responses,
        authoritative: true,
        requiresRecoveryCode: false,
        ambiguousDeviceMatch: ambiguous,
        requiresLogin: !authenticatedMember,
        requiresProfile: Boolean(authenticatedMember && !isProfileComplete(authenticatedMember))
    };
}

async function requireAuthenticatedMember(req, res) {
    const sessionToken = sanitizeText(
        req.body?.sessionToken
        || req.headers['x-session-token']
        || '',
        SESSION_TOKEN_MAX
    );
    if (!sessionToken) {
        console.warn('[auth] 401 no token on %s %s', req.method, req.path);
        res.status(401).json({ error: 'Session token required.' });
        return null;
    }
    const member = await resolveMemberBySessionToken(sessionToken);
    if (!member) {
        // Log the shape, never the token. A 3-part value is a Firebase ID token;
        // anything else is our opaque one. Length matters: a value sitting exactly on
        // SESSION_TOKEN_MAX is the signature of truncation rather than a bad token.
        console.warn('[auth] 401 %s %s len=%d kind=%s',
            req.method, req.path, sessionToken.length,
            sessionToken.split('.').length === 3 ? 'firebase-jwt' : 'opaque');
        res.status(401).json({ error: 'Session expired. Please sign in again.' });
        return null;
    }
    return { member, sessionToken };
}

async function ensureMembershipAccess(memberId, connectionCode) {
    if (!memberId || !connectionCode) return null;
    const normalizedCode = String(connectionCode || '').toLowerCase();
    let membership = await ConnectionMembership.findOne({
        memberId,
        connectionCode: normalizedCode,
        archivedAt: null
    }).lean();
    if (membership) {
        return membership;
    }

    const session = await Session.findOne({ connectionCode: normalizedCode }).lean();
    const participant = Array.isArray(session?.participants)
        ? session.participants.find(item => normalizeId(item?.memberId) === normalizeId(memberId))
        : null;

    if (!participant) {
        return null;
    }

    await upsertConnectionMembership({
        memberId,
        connectionCode: normalizedCode,
        role: normalizeRole(participant.role, 'PARTNER'),
        partnerDisplayNameCache: sanitizeText(participant.partnerLabel || '', 80) || null
    });

    membership = await ConnectionMembership.findOne({
        memberId,
        connectionCode: normalizedCode,
        archivedAt: null
    }).lean();

    return membership;
}

function broadcastToConnection(connectionCode, builder) {
    const connection = connections.get(String(connectionCode || '').toLowerCase());
    if (!connection) return;

    // Deduplication Set: ensure we only send to each unique device once per broadcast.
    // This prevents double-delivery if a single device has multiple ghost connections.
    const sentDevices = new Set();

    connection.clients.forEach(clientWs => {
        if (clientWs.readyState !== WebSocket.OPEN) return;
        const client = clients.get(clientWs);
        if (!client) return;

        // Skip if we've already sent to this device in this broadcast
        if (client.deviceId && sentDevices.has(client.deviceId)) {
            console.log(`♻️ [DEDUPLICATION] Skipping broadcast for duplicate session: ${client.id} (Device: ${client.deviceId})`);
            return;
        }

        const message = builder(clientWs, client);
        if (message) {
            if (client.deviceId) sentDevices.add(client.deviceId);
            sendToClient(clientWs, message);
        }
    });
}

// ── Live (nearby posts + meet-up tracking) ───────────────────────────────────
// Mounted here because it needs the auth helper, the sanitiser and the socket
// fan-out that are all defined above. See plans/live_nearby_implementation.md
require('./live_routes')({
    app, gigiConn, mongoose, sanitizeText, requireAuthenticatedMember,
    ConnectionMembership, Member, broadcastToConnection, normalizeId, logEvent,
    resolvePlanForMember, getAppSettings, admin
});

async function broadcastSharedAlarmUpsert(alarm) {
    const serialized = serializeSharedAlarm(alarm);
    if (!serialized) return;
    broadcastToConnection(serialized.connectionCode, (_ws, client) => ({
        connectionId: serialized.connectionCode,
        senderDeviceId: 'server',
        recipientDeviceId: client.deviceId || null,
        actionType: 'shared_alarm_upserted',
        payload: serialized,
        legacyType: 'shared_alarm_upserted',
        legacyPayload: serialized
    }));
}

async function broadcastSharedAlarmDeleted(connectionCode, alarmId) {
    const payload = {
        connectionCode: String(connectionCode || '').toLowerCase(),
        alarmId: sanitizeText(alarmId || '', 120)
    };
    broadcastToConnection(payload.connectionCode, (_ws, client) => ({
        connectionId: payload.connectionCode,
        senderDeviceId: 'server',
        recipientDeviceId: client.deviceId || null,
        actionType: 'shared_alarm_deleted',
        payload,
        legacyType: 'shared_alarm_deleted',
        legacyPayload: payload
    }));
}

async function refreshMembershipPartnerCaches(connectionCode) {
    const session = await Session.findOne({ connectionCode: connectionCode?.toLowerCase() }).lean();
    if (!session) return;

    for (const participant of session.participants || []) {
        if (!participant?.memberId) continue;

        const partner = getPartnerParticipant(session, participant.memberId, participant.deviceId);
        await upsertConnectionMembership({
            memberId: participant.memberId,
            connectionCode: session.connectionCode,
            role: participant.role || (normalizeId(participant.deviceId) === normalizeId(session.creatorDeviceId) ? 'CREATOR' : 'PARTNER'),
            partnerDisplayNameCache: participant.partnerLabel || partner?.deviceName || null
        });
    }
}

async function refreshMembershipPartnerCachesForMember(memberId) {
    if (!memberId) return;
    const memberships = await ConnectionMembership.find({
        memberId,
        archivedAt: null
    }).lean();
    for (const membership of memberships) {
        await refreshMembershipPartnerCaches(membership.connectionCode);
    }
}

async function broadcastProfileUpdated(memberId) {
    if (!memberId) return;
    const member = await Member.findOne({ memberId, revokedAt: null }).lean();
    if (!member) return;
    const payload = {
        memberId: member.memberId,
        displayName: sanitizeText(member.displayName || '', 80) || null,
        gender: normalizeGender(member.gender),
        avatarUrl: sanitizeText(member.avatarUrl || '', 240) || null,
        themeSongTitle: sanitizeText(member.themeSongTitle || '', 120) || null,
        themeSongUrl: sanitizeText(member.themeSongUrl || '', 512) || null,
        profileComplete: isProfileComplete(member)
    };

    wss.clients.forEach(ws => {
        const client = clients.get(ws);
        if (!client || client.memberId !== memberId || ws.readyState !== WebSocket.OPEN) return;
        sendToClient(ws, {
            connectionId: client.connectionCode || null,
            senderDeviceId: 'server',
            actionType: 'profile_updated',
            payload,
            legacyType: 'profile_updated',
            legacyPayload: payload
        });
    });
}

/**
 * Helper to send FCM data-only push specifically to a member's partner(s) 
 */
async function sendFcmPushToPartner(senderMemberId, payload) {
    try {
        const senderMember = await Member.findOne({ memberId: senderMemberId, revokedAt: null }).lean();
        const senderName = payload.senderName || senderMember?.displayName || 'Your Partner';

        const memberships = await ConnectionMembership.find({
            memberId: senderMemberId,
            archivedAt: null
        }).lean();

        const seenPartners = new Set();
        for (const membership of memberships) {
            const code = membership.connectionCode.toLowerCase();
            const liveConn = connections.get(code);

            const partners = await ConnectionMembership.find({
                connectionCode: code,
                memberId: { $ne: senderMemberId },
                archivedAt: null
            }).lean();

            for (const partner of partners) {
                if (seenPartners.has(partner.memberId)) continue;
                seenPartners.add(partner.memberId);

                // Skip sending FCM push if partner is actively connected live on WebSocket
                if (liveConn && liveConn.clients) {
                    const isPartnerLive = liveConn.clients.some(c => 
                        String(c.memberId || '') === String(partner.memberId || '') && 
                        c.ws && c.ws.readyState === 1
                    );
                    if (isPartnerLive) {
                        console.log(`[FCM] Partner ${partner.memberId} is live on WebSocket, skipping FCM push`);
                        continue;
                    }
                }

                const partnerMember = await Member.findOne({
                    memberId: partner.memberId,
                    revokedAt: null
                }).lean();

                if (partnerMember && partnerMember.fcmToken) {
                    console.log(`[FCM] Sending data push to ${partner.memberId} from ${senderName} for event ${payload.actionType || payload.type}`);
                    const stringData = { senderName };
                    Object.entries(payload).forEach(([key, value]) => {
                        if (value !== null && value !== undefined) {
                            stringData[key] = String(value);
                        }
                    });
                    stringData.timestamp = new Date().toISOString();

                    // Send data-only payload so Android client handles debounced background notification
                    const message = {
                        data: stringData,
                        token: partnerMember.fcmToken,
                        android: {
                            priority: 'high',
                            ttl: 86400 * 1000
                        }
                    };

                    try {
                        await admin.messaging().send(message);
                        console.log(`[FCM] Push delivered to ${partner.memberId}`);
                    } catch (pushErr) {
                        console.error(`[FCM] Delivery failed to ${partner.memberId}:`, pushErr.message);
                        if (pushErr.code === 'messaging/registration-token-not-registered') {
                            await Member.updateOne({ memberId: partner.memberId }, { $set: { fcmToken: null } });
                        }
                    }
                }
            }
        }
    } catch (err) {
        console.error('[FCM] sendFcmPushToPartner global error:', err.message);
    }
}

/** FCM push to the peers of ONE connection, skipping members already connected via WS. */
async function sendFcmToConnectionPeers(connectionCode, senderMemberId, connectedMemberIds, payload) {
    try {
        const code = String(connectionCode || '').toLowerCase();
        if (!code) return;
        const partners = await ConnectionMembership.find({
            connectionCode: code, memberId: { $ne: senderMemberId }, archivedAt: null
        }).lean();
        const seen = new Set();
        for (const partner of partners) {
            if (!partner.memberId || seen.has(partner.memberId)) continue;
            seen.add(partner.memberId);
            if (connectedMemberIds && connectedMemberIds.has(normalizeId(partner.memberId))) continue; // got it live
            const pm = await Member.findOne({ memberId: partner.memberId, revokedAt: null }).lean();
            if (!pm || !pm.fcmToken) continue;
            const stringData = {};
            Object.entries(payload).forEach(([k, v]) => { if (v !== null && v !== undefined) stringData[k] = String(v); });
            stringData.timestamp = new Date().toISOString();
            try {
                await admin.messaging().send({ data: stringData, token: pm.fcmToken, android: { priority: 'high' } });
            } catch (e) {
                if (e.code === 'messaging/registration-token-not-registered') {
                    await Member.updateOne({ memberId: partner.memberId }, { $set: { fcmToken: null } });
                }
            }
        }
    } catch (e) {
        console.error('[FCM] chat peers push failed:', e.message);
    }
}

async function broadcastPartnerProfileUpdated(memberId) {
    if (!memberId) return;
    const member = await Member.findOne({ memberId, revokedAt: null }).lean();
    if (!member) return;

    const payload = {
        memberId: member.memberId,
        displayName: sanitizeText(member.displayName || '', 80) || null,
        gender: normalizeGender(member.gender),
        avatarUrl: sanitizeText(member.avatarUrl || '', 240) || null,
        themeSongTitle: sanitizeText(member.themeSongTitle || '', 120) || null,
        themeSongUrl: sanitizeText(member.themeSongUrl || '', 512) || null,
        profileComplete: isProfileComplete(member)
    };

    // FCM Notification
    sendFcmPushToPartner(memberId, {
        type: 'partner_profile_updated',
        memberId: member.memberId,
        actionType: 'partner_profile_updated'
    });

    const memberships = await ConnectionMembership.find({
        memberId,
        archivedAt: null
    }).lean();

    const seenConnections = new Set();
    memberships.forEach(membership => {
        const connectionCode = String(membership?.connectionCode || '').toLowerCase();
        if (!connectionCode || seenConnections.has(connectionCode)) return;
        seenConnections.add(connectionCode);

        broadcastToConnection(connectionCode, (_ws, client) => {
            if (!client || client.memberId === memberId) return null;
            return {
                connectionId: connectionCode,
                senderDeviceId: 'server',
                recipientDeviceId: client.deviceId || null,
                actionType: 'partner_profile_updated',
                payload,
                legacyType: 'partner_profile_updated',
                legacyPayload: payload
            };
        });
    });
}

async function buildDashboardParticipants(session) {
    const participants = Array.isArray(session?.participants) ? session.participants : [];
    return Promise.all(participants.map(async participant => {
        const deviceId = participant?.deviceId || null;
        const device = deviceId ? await Device.findOne({ deviceId }).lean() : null;
        const liveSessions = deviceId ? getActiveSessionsForDevice(deviceId) : [];
        return {
            clientId: participant?.clientId || null,
            deviceId,
            deviceName: sanitizeText(participant?.deviceName || device?.deviceName || 'Unknown device', 80),
            partnerLabel: sanitizeText(participant?.partnerLabel || '', 80) || null,
            role: normalizeRole(
                participant?.role,
                normalizeId(deviceId) === normalizeId(session?.creatorDeviceId) ? 'CREATOR' : 'PARTNER'
            ),
            isOnline: liveSessions.length > 0 || Boolean(device?.isOnline),
            lastSeenAt: device?.lastSeen ? new Date(device.lastSeen).toISOString() : null,
            activeSessionCount: liveSessions.length || device?.activeSessionCount || 0
        };
    }));
}

function buildSessionTitle(session, participants) {
    const aliases = participants
        .map(participant => participant.partnerLabel)
        .filter(Boolean);
    if (aliases.length >= 2) {
        return aliases.join(' <> ');
    }
    const names = participants
        .map(participant => participant.deviceName)
        .filter(Boolean);
    if (names.length >= 2) {
        return names.join(' <> ');
    }
    return (session?.connectionCode || 'Session').toUpperCase();
}

function summarizeSystemEvent(eventType, data = {}) {
    switch (eventType) {
        case 'created':
            return `${data.deviceName || 'A device'} created the connection`;
        case 'joined':
            return `${data.deviceName || 'A device'} joined the connection`;
        case 'rejoined':
            return `${data.deviceName || 'A device'} rejoined the connection`;
        case 'shared_alarm_upserted':
            return `${data.ownerDisplayName || 'A partner'} saved "${data.title || 'a shared alarm'}"`;
        case 'shared_alarm_deleted':
            return `${data.ownerDisplayName || 'A partner'} removed a shared alarm`;
        case 'profile_updated':
            return `${data.displayName || 'A partner'} updated their profile`;
        case 'card_stack_sent':
            return `${data.senderName || 'A partner'} sent ${data.cardCount || 0} love cards`;
        case 'card_stack_opened':
            return `${data.title || 'A love card deck'} was opened`;
        case 'card_stack_answered':
            return `${data.title || 'A love card deck'} came back with answers`;
        case 'closed':
            return 'A socket closed';
        case 'disconnected':
            return 'A participant disconnected';
        case 'idle_timeout':
            return `Connection idle timeout after ${data.idleMinutes || 30} minutes`;
        default:
            return truncateString(eventType.replace(/_/g, ' '), 120);
    }
}

function parseCaptureBuffer(buffer) {
    if (!Buffer.isBuffer(buffer)) return null;

    try {
        const decompressed = zlib.gunzipSync(buffer);
        const json = JSON.parse(decompressed.toString());
        const hasMedia = Boolean(json?.mediaBase64 || json?.mediaUrl);
        const strokeCount = Array.isArray(json?.strokes) ? json.strokes.length : 0;
        return {
            storageMode: 'json',
            renderMode: 'json',
            captureKind: hasMedia ? 'image' : 'scribble',
            mediaType: json?.mediaType || null,
            strokeCount,
            hasMedia,
            connectionId: json?.connectionId || null,
            scribbleId: json?.scribbleId || null,
            title: hasMedia ? 'Image capture' : 'Scribble',
            summary: hasMedia
                ? `${json?.mediaType || 'media'} • ${strokeCount} strokes`
                : `${strokeCount} strokes`,
            previewStrokes: Array.isArray(json?.strokes) ? json.strokes.slice(0, 5) : [],
            payload: compactArchiveData(json),
            rawJson: json
        };
    } catch (error) {
        // Not a gzipped JSON capture.
    }

    const asciiHeader = buffer.slice(0, Math.min(buffer.length, 256)).toString('utf8');

    if (asciiHeader.startsWith('PHOTO_DATA:')) {
        const parts = asciiHeader.split(':');
        const photoId = sanitizeText(parts[1] || '', 120) || null;
        const headerLength = Buffer.byteLength(`PHOTO_DATA:${parts[1] || ''}:`, 'utf8');
        const payload = buffer.subarray(Math.min(headerLength, buffer.length));
        const mimeType = guessBinaryMimeType(payload, 'image/jpeg');
        return {
            storageMode: 'binary',
            renderMode: 'direct',
            captureKind: 'image',
            mediaType: mimeType,
            payloadBuffer: payload,
            payloadId: photoId,
            title: 'Full photo',
            summary: photoId ? `Photo ${photoId}` : 'Remote photo'
        };
    }

    if (asciiHeader.startsWith('FILE_DATA:')) {
        const parts = asciiHeader.split(':');
        const fileId = sanitizeText(parts[1] || '', 120) || null;
        const originalName = sanitizeFileName(parts[2] || 'download.bin');
        const headerLength = Buffer.byteLength(`FILE_DATA:${parts[1] || ''}:${parts[2] || ''}:`, 'utf8');
        const payload = buffer.subarray(Math.min(headerLength, buffer.length));
        return {
            storageMode: 'binary',
            renderMode: 'download',
            captureKind: 'file',
            mediaType: guessBinaryMimeType(payload),
            payloadBuffer: payload,
            payloadId: fileId,
            originalName,
            title: 'File download',
            summary: originalName
        };
    }

    if (asciiHeader.startsWith('AUDIO_DATA:')) {
        const parts = asciiHeader.split(':');
        const audioId = sanitizeText(parts[1] || '', 120) || null;
        const headerLength = Buffer.byteLength(`AUDIO_DATA:${parts[1] || ''}:`, 'utf8');
        const payload = buffer.subarray(Math.min(headerLength, buffer.length));
        const mimeType = guessBinaryMimeType(payload, 'audio/mp4');
        return {
            storageMode: 'binary',
            renderMode: 'download',
            captureKind: 'audio',
            mediaType: mimeType,
            payloadBuffer: payload,
            payloadId: audioId,
            title: 'Audio capture',
            summary: audioId ? `Clip ${audioId}` : 'Remote audio'
        };
    }

    if (asciiHeader.startsWith('LIVE_VIDEO:')) {
        return {
            storageMode: 'transient',
            renderMode: 'none',
            captureKind: 'live_video',
            mediaType: 'video/raw',
            title: 'Live camera frame',
            summary: 'Transient stream frame'
        };
    }

    const mimeType = guessBinaryMimeType(buffer);
    return {
        storageMode: 'binary',
        renderMode: mimeType.startsWith('image/') ? 'direct' : 'download',
        captureKind: mimeType.startsWith('image/') ? 'image' : 'file',
        mediaType: mimeType,
        payloadBuffer: buffer,
        title: mimeType.startsWith('image/') ? 'Image asset' : 'Binary file',
        summary: truncateString(mimeType, 120)
    };
}

function listCaptureFiles(connectionCode) {
    const sessionDir = path.join(CAPTURES_DIR, String(connectionCode || '').toLowerCase());
    if (!fs.existsSync(sessionDir)) return [];

    return fs.readdirSync(sessionDir)
        .filter(fileName => !fileName.startsWith('.') && fileName !== 'traffic.log')
        .map(fileName => {
            const filePath = path.join(sessionDir, fileName);
            const stats = fs.statSync(filePath);
            return {
                fileName,
                filePath,
                size: stats.size,
                timestampMs: extractTimestampFromCaptureFile(fileName, stats.mtimeMs)
            };
        })
        .sort((a, b) => a.timestampMs - b.timestampMs);
}

function buildCommandSummary(command, payload) {
    switch (command) {
        case 'get_location':
            return 'Requested current location';
        case 'take_remote_photo':
            return `Requested a ${payload?.cameraType || 'back'} camera photo`;
        case 'get_photo_list':
            return 'Requested photo library';
        case 'get_full_photo':
            return `Requested full photo ${payload?.photoId || ''}`.trim();
        case 'get_file_list':
            return `Requested files from ${payload?.path || 'Root'}`;
        case 'get_file_data':
            return `Requested file ${payload?.path || ''}`.trim();
        case 'photo_list_result':
            return `${Array.isArray(payload?.photos) ? payload.photos.length : 0} photos returned`;
        case 'file_list_result':
            return `${Array.isArray(payload?.files) ? payload.files.length : 0} files returned`;
        case 'notification_posted':
            return 'Notification mirrored';
        default:
            return truncateString(command.replace(/_/g, ' '), 160);
    }
}

function formatRelativeDueLabel(dueAt) {
    const timestamp = Number(dueAt || 0);
    if (!timestamp) return 'scheduled';
    const diff = timestamp - Date.now();
    const abs = Math.abs(diff);
    const minute = 60 * 1000;
    const hour = 60 * minute;
    const day = 24 * hour;
    const suffix = diff >= 0 ? 'from now' : 'ago';
    if (abs < hour) return `${Math.max(1, Math.round(abs / minute))}m ${suffix}`;
    if (abs < day) return `${Math.max(1, Math.round(abs / hour))}h ${suffix}`;
    return `${Math.max(1, Math.round(abs / day))}d ${suffix}`;
}

async function buildDashboardArchive(connectionCode) {
    const code = String(connectionCode || '').toLowerCase();
    const session = await Session.findOne({ connectionCode: code }).lean();
    if (!session) return null;

    const pairModels = await getPairModels(code);
    const actions = pairModels
        ? await pairModels.Action.find({ connectionCode: code }).sort({ createdAt: 1 }).lean()
        : [];
    const notifications = pairModels
        ? await pairModels.Notification.find().sort({ createdAt: -1 }).limit(25).lean()
        : [];
    const participants = await buildDashboardParticipants(session);
    const participantMap = new Map(
        participants.map(participant => [normalizeId(participant.deviceId), participant])
    );
    const actionByMessageId = new Map(actions.map(action => [String(action.messageId), action]));
    const actionByCaptureFile = new Map(
        actions
            .map(action => [getCaptureFileName(action.assetRef), action])
            .filter(([fileName]) => Boolean(fileName))
    );

    const feed = [];
    const gallery = [];

    for (const event of session.events || []) {
        const timestamp = toIsoTimestamp(event?.timestamp || session.updatedAt || session.createdAt);
        const eventData = event?.data && typeof event.data === 'object' ? event.data : {};
        const actor = participantMap.get(normalizeId(eventData.senderDeviceId || eventData.deviceId || eventData.targetDeviceId));

        if (event?.type === 'message') {
            feed.push({
                id: eventData.messageId || `event-${timestamp}-message`,
                category: 'message',
                timestamp,
                actorName: actor?.deviceName || eventData.senderName || 'Partner',
                actorDeviceId: eventData.senderDeviceId || null,
                title: 'Chat',
                summary: eventData.preview || 'Message',
                payload: eventData.payload || null
            });
            continue;
        }

        if (event?.type === 'quote') {
            feed.push({
                id: eventData.messageId || `event-${timestamp}-quote`,
                category: 'quote',
                timestamp,
                actorName: actor?.deviceName || eventData.senderName || 'Partner',
                actorDeviceId: eventData.senderDeviceId || null,
                title: 'Quote',
                summary: truncateString(eventData.text || 'Quote sent', 240),
                payload: {
                    text: truncateString(eventData.text || '', 480)
                }
            });
            continue;
        }

        if (event?.type === 'card_stack_sent' || event?.type === 'card_stack_answered' || event?.type === 'card_stack_opened') {
            feed.push({
                id: eventData.stackId || `event-${timestamp}-${event.type}`,
                category: 'love_cards',
                timestamp,
                actorName: actor?.deviceName || eventData.senderName || 'Partner',
                actorDeviceId: eventData.senderDeviceId || null,
                title: event?.type === 'card_stack_answered'
                    ? 'Love cards answered'
                    : event?.type === 'card_stack_opened'
                        ? 'Love cards opened'
                        : 'Love cards sent',
                summary: event?.type === 'card_stack_answered'
                    ? `${eventData.title || 'Sweet deck'} • ${eventData.answerCount || 0} replies`
                    : event?.type === 'card_stack_opened'
                        ? `${eventData.title || 'Sweet deck'} was opened`
                        : `${eventData.title || 'Sweet deck'} • ${eventData.cardCount || 0} cards`,
                payload: compactArchiveData(eventData)
            });
            continue;
        }

        if (event?.type === 'shared_alarm_upserted' || event?.type === 'shared_alarm_deleted') {
            feed.push({
                id: eventData.alarmId || `event-${timestamp}-${event.type}`,
                category: 'alarm',
                timestamp,
                actorName: eventData.ownerDisplayName || actor?.deviceName || 'Partner',
                actorDeviceId: eventData.ownerMemberId || null,
                title: event.type === 'shared_alarm_deleted' ? 'Shared alarm removed' : 'Shared alarm saved',
                summary: event.type === 'shared_alarm_deleted'
                    ? 'A shared alarm was removed from this connection'
                    : `${eventData.title || 'Shared alarm'} • ${formatRelativeDueLabel(eventData.dueAt)}`,
                payload: compactArchiveData(eventData)
            });
            continue;
        }

        if (event?.type === 'remote_command') {
            const command = sanitizeText(eventData.command || 'remote_command', 120);
            const action = actionByMessageId.get(String(eventData.messageId || ''));
            feed.push({
                id: eventData.messageId || `event-${timestamp}-${command}`,
                category: 'command',
                timestamp,
                actorName: actor?.deviceName || eventData.senderName || (eventData.source === 'dashboard' ? 'Server Console' : 'Partner'),
                actorDeviceId: eventData.senderDeviceId || null,
                targetDeviceId: eventData.targetDeviceId || null,
                title: command.replace(/_/g, ' '),
                command,
                summary: buildCommandSummary(command, eventData.payload || {}),
                payload: eventData.payload || null,
                source: eventData.source || 'device',
                status: action?.status || eventData.status || null
            });
            continue;
        }

        if (event?.type === 'location_heartbeat') {
            feed.push({
                id: `location-${timestamp}-${eventData.senderDeviceId || 'unknown'}`,
                category: 'location',
                timestamp,
                actorName: actor?.deviceName || eventData.senderName || 'Partner',
                actorDeviceId: eventData.senderDeviceId || null,
                title: 'Location update',
                summary: eventData.latitude != null && eventData.longitude != null
                    ? `${Number(eventData.latitude).toFixed(5)}, ${Number(eventData.longitude).toFixed(5)}`
                    : 'Location update received',
                latitude: eventData.latitude ?? null,
                longitude: eventData.longitude ?? null
            });
            continue;
        }

        feed.push({
            id: `event-${timestamp}-${event?.type || 'system'}`,
            category: 'system',
            timestamp,
            actorName: actor?.deviceName || eventData.deviceName || 'System',
            actorDeviceId: eventData.deviceId || null,
            title: 'System event',
            summary: summarizeSystemEvent(event?.type || 'event', eventData),
            eventType: event?.type || 'event'
        });
    }

    for (const notification of notifications) {
        feed.push({
            id: notification?._id?.toString() || `notification-${notification?.createdAt || Date.now()}`,
            category: 'notification',
            timestamp: toIsoTimestamp(notification?.createdAt),
            actorName: participantMap.get(normalizeId(notification?.deviceId))?.deviceName || notification?.deviceId || 'Partner',
            actorDeviceId: notification?.deviceId || null,
            title: notification?.data?.package_name || 'Notification',
            summary: truncateString(`${notification?.data?.title || ''} ${notification?.data?.text || ''}`.trim() || 'Notification mirrored', 240),
            payload: compactArchiveData(notification?.data || {})
        });
    }

    const captureFiles = listCaptureFiles(code);
    const seenGalleryIds = new Set();
    for (const captureFile of captureFiles) {
        try {
            const rawBuffer = fs.readFileSync(captureFile.filePath);
            const parsedCapture = parseCaptureBuffer(rawBuffer);
            if (!parsedCapture || parsedCapture.renderMode === 'none') continue;

            const action = actionByCaptureFile.get(captureFile.fileName.toLowerCase());
            const actor = participantMap.get(normalizeId(action?.senderDeviceId));
            const galleryId = action?.messageId || captureFile.fileName;
            if (seenGalleryIds.has(galleryId)) continue;
            seenGalleryIds.add(galleryId);

            gallery.push({
                id: galleryId,
                timestamp: toIsoTimestamp(action?.createdAt || captureFile.timestampMs),
                actorName: actor?.deviceName || 'Partner',
                actorDeviceId: action?.senderDeviceId || null,
                status: action?.status || null,
                title: parsedCapture.title,
                summary: parsedCapture.summary,
                renderMode: parsedCapture.renderMode,
                captureKind: parsedCapture.captureKind,
                mediaType: parsedCapture.mediaType,
                captureUrl: parsedCapture.renderMode === 'json'
                    ? `/api/dashboard/capture/${code}/${captureFile.fileName}`
                    : toCaptureUrl(code, captureFile.fileName),
                fileName: captureFile.fileName,
                fileSize: captureFile.size
            });

            feed.push({
                id: `capture-${galleryId}`,
                category: 'capture',
                timestamp: toIsoTimestamp(action?.createdAt || captureFile.timestampMs),
                actorName: actor?.deviceName || 'Partner',
                actorDeviceId: action?.senderDeviceId || null,
                title: parsedCapture.title,
                summary: parsedCapture.summary,
                captureKind: parsedCapture.captureKind,
                status: action?.status || null
            });
        } catch (error) {
            console.error(`❌ Failed to read capture ${captureFile.fileName} for ${code}:`, error.message);
        }
    }

    feed.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
    gallery.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));

    const controlTargets = participants
        .filter(participant => participant.role !== 'CREATOR')
        .map(participant => ({
            deviceId: participant.deviceId,
            deviceName: participant.deviceName,
            isOnline: participant.isOnline,
            role: participant.role
        }));

    return {
        connectionCode: code,
        title: buildSessionTitle(session, participants),
        signature: JSON.stringify({
            updatedAt: toIsoTimestamp(session.updatedAt || session.createdAt),
            actions: actions.length,
            events: (session.events || []).length,
            gallery: gallery.length,
            notifications: notifications.length
        }),
        summary: {
            galleryCount: gallery.length,
            feedCount: feed.length,
            participantCount: participants.length,
            notificationCount: notifications.length,
            lastActivityAt: toIsoTimestamp(session.updatedAt || session.createdAt)
        },
        participants,
        controls: {
            targets: controlTargets,
            commands: [
                { command: 'get_location', label: 'Request location', icon: 'location-dot' },
                { command: 'take_remote_photo', label: 'Take photo', icon: 'camera' },
                { command: 'get_photo_list', label: 'Photo library', icon: 'images' },
                { command: 'get_file_list', label: 'File explorer', icon: 'folder-open' }
            ]
        },
        gallery,
        feed
    };
}

async function dispatchDashboardCommand(connectionCode, {
    command,
    targetDeviceId = null,
    data = {}
} = {}) {
    const code = String(connectionCode || '').toLowerCase();
    const normalizedCommand = sanitizeText(command || '', 80);
    if (!DASHBOARD_REMOTE_COMMANDS.has(normalizedCommand)) {
        return { ok: false, status: 400, error: 'Unsupported command' };
    }

    const session = await Session.findOne({ connectionCode: code }).lean();
    if (!session) {
        return { ok: false, status: 404, error: 'Session not found' };
    }

    const participants = await buildDashboardParticipants(session);
    const preferredTargetId = sanitizeText(targetDeviceId || '', 120) || null;
    const resolvedTarget = preferredTargetId
        ? participants.find(participant => normalizeId(participant.deviceId) === normalizeId(preferredTargetId))
        : participants.find(participant => participant.role !== 'CREATOR') || participants[0];

    if (!resolvedTarget?.deviceId) {
        return { ok: false, status: 404, error: 'Target device not found' };
    }

    const targetSessions = getActiveSessionsForDevice(resolvedTarget.deviceId)
        .filter(({ client, ws }) => ws.readyState === WebSocket.OPEN && client?.connectionCode?.toLowerCase() === code);
    if (targetSessions.length === 0) {
        return { ok: false, status: 409, error: 'Target device is offline for this session' };
    }

    const messageId = uuidv4();
    const cleanPayload = data && typeof data === 'object' ? compactArchiveData(data) : {};
    await appendSessionEvent(code, 'remote_command', {
        messageId,
        senderDeviceId: 'dashboard',
        senderName: 'Server Console',
        targetDeviceId: resolvedTarget.deviceId,
        command: normalizedCommand,
        payload: compactCommandPayloadForArchive(normalizedCommand, cleanPayload),
        source: 'dashboard',
        status: 'delivered'
    });

    await upsertActionRecord(code, {
        messageId,
        actionType: 'remote_command',
        senderDeviceId: 'dashboard',
        recipientDeviceId: resolvedTarget.deviceId,
        status: 'delivered',
        requiresDisplayReceipt: false
    });

    targetSessions.forEach(({ ws, client }) => {
        sendToClient(ws, {
            connectionId: code,
            senderDeviceId: 'dashboard',
            recipientDeviceId: client.deviceId,
            actionType: 'remote_command',
            payload: {
                command: normalizedCommand,
                data: cleanPayload,
                source: 'dashboard'
            },
            messageId,
            requiresDisplayReceipt: false,
            legacyType: 'remote_command',
            legacyPayload: {
                command: normalizedCommand,
                data: cleanPayload
            }
        });
    });

    logEvent('dashboard.command.sent', {
        connectionCode: code,
        targetDeviceId: resolvedTarget.deviceId,
        command: normalizedCommand
    });

    return {
        ok: true,
        status: 200,
        payload: {
            success: true,
            messageId,
            targetDeviceId: resolvedTarget.deviceId,
            command: normalizedCommand
        }
    };
}

async function backfillConnectionOwnership() {
    if (!isMongoReady(screensaverConn) || !isMongoReady(gigiConn)) {
        setTimeout(() => {
            backfillConnectionOwnership().catch(err => {
                console.error('❌ Failed deferred ownership backfill:', err);
            });
        }, 2500);
        return;
    }

    await getOrCreateServerStateDocument();
    const sessions = await Session.find().lean();
    for (const session of sessions) {
        const nextParticipants = [];
        let participantsChanged = false;

        for (const participant of session.participants || []) {
            if (!participant?.deviceId) {
                nextParticipants.push(participant);
                continue;
            }

            const role = normalizeId(participant.deviceId) === normalizeId(session.creatorDeviceId)
                ? 'CREATOR'
                : 'PARTNER';
            const { member } = await resolveOrCreateMember({
                deviceId: participant.deviceId,
                deviceName: participant.deviceName,
                allowCreate: true
            });

            if (member?.memberId) {
                await upsertConnectionMembership({
                    memberId: member.memberId,
                    connectionCode: session.connectionCode,
                    role,
                    partnerDisplayNameCache: participant.partnerLabel || participant.deviceName
                });
            }

            const enrichedParticipant = {
                ...participant,
                memberId: member?.memberId || participant.memberId || null,
                role
            };
            if (enrichedParticipant.memberId !== participant.memberId || enrichedParticipant.role !== participant.role) {
                participantsChanged = true;
            }
            nextParticipants.push(enrichedParticipant);
        }

        if (participantsChanged) {
            await Session.updateOne(
                { _id: session._id },
                { $set: { participants: nextParticipants } }
            );
        }
    }
}

function getActiveSessionsForDevice(deviceId) {
    const normalizedDeviceId = normalizeId(deviceId);
    if (!normalizedDeviceId) return [];

    const activeSessions = [];
    wss.clients.forEach(ws => {
        const client = clients.get(ws);
        if (client && normalizeId(client.deviceId) === normalizedDeviceId && ws.readyState === WebSocket.OPEN) {
            activeSessions.push({ ws, client });
        }
    });

    return activeSessions;
}

function clientSupportsV2(client) {
    return Boolean(client?.protocolVersion >= PROTOCOL_VERSION);
}

function normalizeIncomingMessage(message, client) {
    const payload = message?.payload && typeof message.payload === 'object' ? message.payload : {};
    return {
        protocolVersion: Number(message?.protocolVersion || 1),
        messageId: message?.messageId || uuidv4(),
        connectionId: message?.connectionId || message?.connectionCode || client?.connectionCode || null,
        senderDeviceId: message?.senderDeviceId || message?.deviceId || client?.deviceId || null,
        recipientDeviceId: message?.recipientDeviceId || null,
        actionType: message?.actionType || message?.type,
        payload,
        requiresDisplayReceipt: Boolean(message?.requiresDisplayReceipt),
        raw: message
    };
}

function buildEnvelope({
    connectionId,
    senderDeviceId,
    recipientDeviceId = null,
    actionType,
    payload = {},
    messageId = uuidv4(),
    requiresDisplayReceipt = false
}) {
    return JSON.stringify({
        protocolVersion: PROTOCOL_VERSION,
        messageId,
        connectionId,
        senderDeviceId,
        recipientDeviceId,
        actionType,
        payload,
        createdAt: Date.now(),
        requiresDisplayReceipt
    });
}

function sendToClient(ws, {
    connectionId,
    senderDeviceId,
    recipientDeviceId = null,
    actionType,
    payload = {},
    messageId = uuidv4(),
    requiresDisplayReceipt = false,
    legacyType = null,
    legacyPayload = {}
}) {
    if (ws.readyState !== WebSocket.OPEN) return false;
    const client = clients.get(ws);

    if (clientSupportsV2(client)) {
        return ws.send(buildEnvelope({
            connectionId,
            senderDeviceId,
            recipientDeviceId,
            actionType,
            payload,
            messageId,
            requiresDisplayReceipt
        }));
    }

    if (!legacyType) return false;
    return ws.send(JSON.stringify({
        type: legacyType,
        ...(legacyPayload || {})
    }));
}

async function upsertActionRecord(connectionCode, {
    messageId,
    actionType,
    senderDeviceId,
    recipientDeviceId,
    status,
    requiresDisplayReceipt = false,
    assetRef = null,
    lastError = null
}) {
    const pairModels = await getPairModels(connectionCode);
    if (!pairModels) return null;

    const update = {
        $setOnInsert: {
            connectionCode,
            messageId,
            actionType,
            senderDeviceId,
            recipientDeviceId,
            requiresDisplayReceipt,
            createdAt: new Date()
        },
        $set: {
            status,
            lastError
        }
    };

    if (assetRef) {
        update.$set.assetRef = assetRef;
    }

    if (status === 'accepted') update.$set.acceptedAt = new Date();
    if (status === 'delivered') update.$set.deliveredAt = new Date();
    if (status === 'displayed') update.$set.displayedAt = new Date();
    if (status === 'failed') update.$set.failedAt = new Date();

    return pairModels.Action.findOneAndUpdate(
        { connectionCode, messageId },
        update,
        { upsert: true, new: true }
    );
}

async function getRecentReceipts(connectionCode, senderDeviceId, limit = 50) {
    const pairModels = await getPairModels(connectionCode);
    if (!pairModels) return [];

    return pairModels.Action.find({ connectionCode, senderDeviceId })
        .sort({ updatedAt: -1 })
        .limit(limit)
        .lean();
}

async function resolvePartnerPresence(connectionCode, requesterDeviceId) {
    const code = typeof connectionCode === 'string' ? connectionCode.toLowerCase() : null;
    if (!code) {
        return {
            connectionCode: null,
            partnerDeviceId: null,
            partnerDeviceName: null,
            isOnline: false,
            lastSeenAt: null,
            activeSessionCount: 0
        };
    }

    const normalizedRequesterId = normalizeId(requesterDeviceId);
    const liveConnection = connections.get(code);
    const livePartnerClient = liveConnection?.clients
        ?.map(clientWs => clients.get(clientWs))
        ?.find(entry => entry && normalizeId(entry.deviceId) !== normalizedRequesterId);

    let partnerDeviceId = livePartnerClient?.deviceId || null;
    let partnerDeviceName = null;
    const session = await Session.findOne({ connectionCode: code });
    const requesterParticipant = getParticipantRecord(session, null, requesterDeviceId);
    const partnerParticipant = getPartnerParticipant(session, null, requesterDeviceId);
    const preferredPartnerLabel = requesterParticipant?.partnerLabel || null;
    partnerDeviceName = preferredPartnerLabel || livePartnerClient?.deviceName || partnerParticipant?.deviceName || null;

    if (!partnerDeviceId) {
        partnerDeviceId = partnerParticipant?.deviceId || null;
        partnerDeviceName = partnerDeviceName || partnerParticipant?.deviceName || null;
    }

    let isOnline = false;
    let lastSeenAt = null;
    let activeSessionCount = 0;
    if (partnerDeviceId) {
        const liveSessions = getActiveSessionsForDevice(partnerDeviceId);
        isOnline = liveSessions.length > 0;
        activeSessionCount = liveSessions.length;

        if (!isOnline) {
            const partnerDevice = await Device.findOne({ deviceId: partnerDeviceId });
            const lastSeenMs = partnerDevice?.lastSeen ? new Date(partnerDevice.lastSeen).getTime() : 0;
            lastSeenAt = lastSeenMs || null;
            activeSessionCount = partnerDevice?.activeSessionCount || activeSessionCount;
            isOnline = Boolean(
                partnerDevice?.isOnline &&
                lastSeenMs &&
                Date.now() - lastSeenMs <= PRESENCE_STALE_MS
            );
            partnerDeviceName = partnerDeviceName || partnerDevice?.deviceName || null;
        } else {
            const partnerDevice = await Device.findOne({ deviceId: partnerDeviceId });
            lastSeenAt = partnerDevice?.lastSeen ? new Date(partnerDevice.lastSeen).getTime() : Date.now();
        }
    }

    return {
        connectionCode: code,
        partnerDeviceId,
        partnerDeviceName,
        isOnline,
        lastSeenAt,
        activeSessionCount
    };
}

async function updateDevicePresence(deviceId, deviceName, clientId, isOnline, connectionCode = null) {
    if (!deviceId) return;
    try {
        const activeSessions = getActiveSessionsForDevice(deviceId);
        const effectiveOnline = isOnline || activeSessions.length > 0;
        const effectiveClientId = effectiveOnline
            ? (activeSessions[0]?.client?.id || clientId)
            : clientId;
        const effectiveConnectionCode = connectionCode || activeSessions[0]?.client?.connectionCode || null;

        if (!isOnline && activeSessions.length > 0) {
            console.log(`ℹ️ [PRESENCE] Device ${deviceId} still has ${activeSessions.length} active sessions. Keeping presence ONLINE.`);
        }

        await Device.findOneAndUpdate(
            { deviceId },
            {
                deviceName,
                clientId: effectiveClientId,
                isOnline: effectiveOnline,
                lastSeen: new Date(),
                activeSessionCount: activeSessions.length,
                lastConnectionCode: effectiveConnectionCode,
                presenceUpdatedAt: new Date()
            },
            { upsert: true, new: true }
        );
        logEvent('presence.updated', {
            deviceId,
            isOnline: effectiveOnline,
            activeSessionCount: activeSessions.length,
            connectionCode: effectiveConnectionCode
        });
    } catch (err) {
        console.error('❌ Failed to update device presence:', err);
    }
}

async function sendPresenceSnapshot(ws, connectionCode, requesterDeviceId, {
    showPopup = true,
    actionType = 'presence_snapshot',
    messageId = uuidv4()
} = {}) {
    const presence = await resolvePartnerPresence(connectionCode, requesterDeviceId);
    sendToClient(ws, {
        connectionId: presence.connectionCode,
        senderDeviceId: 'server',
        actionType,
        payload: {
            connectionCode: presence.connectionCode,
            partnerDeviceId: presence.partnerDeviceId,
            partnerDeviceName: presence.partnerDeviceName,
            isOnline: presence.isOnline,
            lastSeenAt: presence.lastSeenAt,
            activeSessionCount: presence.activeSessionCount,
            showPopup
        },
        messageId,
        legacyType: 'partner_presence',
        legacyPayload: {
            connectionId: presence.connectionCode,
            partnerDeviceId: presence.partnerDeviceId,
            partnerDeviceName: presence.partnerDeviceName,
            isOnline: presence.isOnline,
            lastSeenAt: presence.lastSeenAt,
            showPopup
        }
    });
    return presence;
}

const broadcastCooldowns = new Map();

async function broadcastPartnerStatus(connectionCode, changedDeviceId, showPopup = true) {
    const code = typeof connectionCode === 'string' ? connectionCode.toLowerCase() : null;
    if (!code) return;

    // Throttle broadcasts to once every 3 seconds per connection
    const lastBroadcast = broadcastCooldowns.get(code) || 0;
    if (Date.now() - lastBroadcast < 3000) {
        return;
    }
    broadcastCooldowns.set(code, Date.now());

    const connection = connections.get(code);
    if (!connection) return;

    for (const partnerWs of connection.clients) {
        const partnerClient = clients.get(partnerWs);
        if (!partnerClient || normalizeId(partnerClient.deviceId) === normalizeId(changedDeviceId)) {
            continue;
        }
        await sendPresenceSnapshot(partnerWs, code, partnerClient.deviceId, {
            showPopup,
            actionType: 'partner_status_changed'
        });
    }
}

async function collectActionMetrics() {
    let queuedActionCount = 0;
    let averageActionLatencyMs = 0;
    let latencySamples = 0;

    for (const [, pairModels] of connectionDbs.entries()) {
        queuedActionCount += await pairModels.Action.countDocuments({
            status: { $in: ['accepted'] }
        });

        const deliveredActions = await pairModels.Action.find({
            deliveredAt: { $ne: null },
            acceptedAt: { $ne: null }
        }).lean();

        deliveredActions.forEach(action => {
            const deliveredAt = action.deliveredAt ? new Date(action.deliveredAt).getTime() : 0;
            const acceptedAt = action.acceptedAt ? new Date(action.acceptedAt).getTime() : 0;
            if (deliveredAt > acceptedAt) {
                averageActionLatencyMs += (deliveredAt - acceptedAt);
                latencySamples += 1;
            }
        });
    }

    return {
        queuedActionCount,
        averageActionLatencyMs: latencySamples > 0 ? Math.round(averageActionLatencyMs / latencySamples) : 0
    };
}

app.get('/healthz', async (req, res) => {
    res.json({
        status: 'ok',
        uptimeSeconds: Math.round(process.uptime()),
        serverTime: Date.now()
    });
});

app.get('/readyz', async (req, res) => {
    const ready = isMongoReady(screensaverConn) && isMongoReady(gigiConn);
    res.status(ready ? 200 : 503).json({
        status: ready ? 'ready' : 'not_ready',
        screensaverDbReady: isMongoReady(screensaverConn),
        gigiDbReady: isMongoReady(gigiConn)
    });
});

app.get('/admin/metrics', requireAdminAuth, async (req, res) => {
    const onlineDevices = await Device.countDocuments({ isOnline: true });
    const actionMetrics = await collectActionMetrics();

    res.json({
        activeSessions: connections.size,
        onlineDevices,
        reconnectCount: metrics.reconnectCount,
        queuedActionCount: actionMetrics.queuedActionCount,
        retryableFailureCount: metrics.retryableFailureCount,
        permanentFailureCount: metrics.permanentFailureCount,
        averageActionLatencyMs: actionMetrics.averageActionLatencyMs,
        scannedConnectionDbs: connectionDbs.size
    });
});

app.get('/admin/sessions/:connectionCode', requireAdminAuth, async (req, res) => {
    const connectionCode = req.params.connectionCode.toLowerCase();
    const session = await Session.findOne({ connectionCode }).lean();
    if (!session) {
        return res.status(404).json({ error: 'Session not found' });
    }

    const pairModels = await getPairModels(connectionCode);
    const actions = pairModels
        ? await pairModels.Action.find({ connectionCode }).sort({ updatedAt: -1 }).limit(50).lean()
        : [];

    const participantPresence = await Promise.all(
        (session.participants || []).map(async participant => {
            const device = await Device.findOne({ deviceId: participant.deviceId }).lean();
            return {
                deviceId: participant.deviceId,
                deviceName: participant.deviceName,
                isOnline: Boolean(device?.isOnline),
                lastSeen: device?.lastSeen || null,
                activeSessionCount: device?.activeSessionCount || 0
            };
        })
    );

    res.json({
        session,
        participants: participantPresence,
        currentPresence: connections.get(connectionCode)?.clients?.length || 0,
        lastActions: actions,
        lastErrors: actions.filter(action => action.status === 'failed').slice(0, 10)
    });
});

app.get('/admin/devices/:deviceId', requireAdminAuth, async (req, res) => {
    const deviceId = req.params.deviceId;
    const device = await Device.findOne({ deviceId }).lean();
    if (!device) {
        return res.status(404).json({ error: 'Device not found' });
    }

    res.json({
        device,
        liveSessions: getActiveSessionsForDevice(deviceId).map(({ client }) => ({
            clientId: client.id,
            connectionCode: client.connectionCode,
            deviceName: client.deviceName
        }))
    });
});

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN: Token-Based Session Auth (for dashboard)
// ─────────────────────────────────────────────────────────────────────────────

const adminSessions = new Map(); // token -> { createdAt, expiresAt }
const ADMIN_TOKEN_TTL_MS = 8 * 60 * 60 * 1000; // 8 hours

function extractAdminToken(req) {
    const auth = req.headers['authorization'] || '';
    if (auth.startsWith('Bearer ')) return auth.slice(7);
    return req.headers['x-admin-token'] || null;
}

function requireAdminTokenOrBasic(req, res, next) {
    const token = extractAdminToken(req);
    if (token) {
        const session = adminSessions.get(token);
        if (session && session.expiresAt > Date.now()) return next();
        return res.status(401).json({ error: 'Invalid or expired admin token' });
    }
    return requireAdminAuth(req, res, next);
}

app.post('/admin/login', (req, res) => {
    if (!ADMIN_USERNAME || !ADMIN_PASSWORD) {
        return res.status(503).json({ error: 'Admin auth not configured. Set ADMIN_USERNAME and ADMIN_PASSWORD env vars.' });
    }
    const { username = '', password = '' } = req.body || {};
    const uBuf = Buffer.from(username); const euBuf = Buffer.from(ADMIN_USERNAME);
    const pBuf = Buffer.from(password); const epBuf = Buffer.from(ADMIN_PASSWORD);
    const uOk = uBuf.length === euBuf.length && crypto.timingSafeEqual(uBuf, euBuf);
    const pOk = pBuf.length === epBuf.length && crypto.timingSafeEqual(pBuf, epBuf);
    if (!uOk || !pOk) return res.status(401).json({ error: 'Invalid credentials' });
    const token = crypto.randomBytes(32).toString('hex');
    const now = Date.now();
    adminSessions.set(token, { createdAt: now, expiresAt: now + ADMIN_TOKEN_TTL_MS });
    res.json({ token, expiresAt: now + ADMIN_TOKEN_TTL_MS });
});

app.post('/admin/logout', requireAdminTokenOrBasic, (req, res) => {
    const token = extractAdminToken(req);
    if (token) adminSessions.delete(token);
    res.json({ ok: true });
});

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN: Rich Stats
// ─────────────────────────────────────────────────────────────────────────────

app.get('/admin/data/stats', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const [memberCount, deviceCount, onlineDevices, totalMemberships, activeAlarms, totalCards] = await Promise.all([
            Member.countDocuments(),
            Device.countDocuments(),
            Device.countDocuments({ isOnline: true }),
            ConnectionMembership.countDocuments(),
            SharedAlarm.countDocuments({ isActive: true }),
            LoveCardStack.countDocuments()
        ]);
        // Use whichever auth model has data — Member (new OTP auth) or Device (legacy)
        const totalMembers = memberCount > 0 ? memberCount : deviceCount;
        // Total connections = unique live connection codes in memory + any stored memberships
        const liveConnectionCodes = connections.size;
        const storedConnectionCodes = await ConnectionMembership.distinct('connectionCode').then(arr => arr.length).catch(() => 0);
        const totalSessions = Math.max(liveConnectionCodes, storedConnectionCodes);
        res.json({
            totalMembers,
            totalSessions,
            onlineDevices,
            totalDevices: deviceCount,
            totalMemberships,
            activeAlarms,
            totalCards,
            liveWebSocketClients: wss.clients.size,
            liveConnections: connections.size
        });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN: Members CRUD
// ─────────────────────────────────────────────────────────────────────────────

app.get('/admin/data/members', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const page = Math.max(1, parseInt(req.query.page) || 1);
        const limit = Math.min(100, parseInt(req.query.limit) || 50);
        const skip = (page - 1) * limit;
        const search = req.query.search || '';

        // Try new Member collection first; fall back to Device collection (legacy auth)
        const memberCount = await Member.countDocuments();
        if (memberCount > 0) {
            const query = search ? { $or: [
                { displayName: { $regex: search, $options: 'i' } },
                { phoneNumber: { $regex: search, $options: 'i' } },
                { googleEmail: { $regex: search, $options: 'i' } }
            ]} : {};
            const [total, members] = await Promise.all([
                Member.countDocuments(query),
                Member.find(query).sort({ createdAt: -1 }).skip(skip).limit(limit).lean()
            ]);
            const planCfg = await getPlanConfig();
            const enriched = await Promise.all(members.map(async m => {
                const [memberships, device] = await Promise.all([
                    ConnectionMembership.find({ memberId: m.memberId }).lean(),
                    m.primaryDeviceId ? Device.findOne({ deviceId: m.primaryDeviceId }).lean() : null
                ]);
                const resolvedPlan = await resolvePlanForMember(m);
                const creatorCount = memberships.filter(x => x.role === 'CREATOR' && !x.archivedAt).length;
                return {
                    ...m,
                    connectionCount: memberships.length,
                    isOnline: device?.isOnline || false,
                    // Normalised display fields for the admin UI
                    phoneNumber: m.phoneNumber || null,
                    email: m.googleEmail || null,
                    deviceName: device?.deviceName || m.deviceName || 'Unknown device',
                    // Monetization fields for the admin UI
                    tier: m.tier || 'free',
                    planOverrides: m.planOverrides || {},
                    resolvedPlan,
                    creatorConnectionCount: creatorCount
                };
            }));
            return res.json({ members: enriched, total, page, pages: Math.ceil(total / limit) });
        }

        // Legacy: show Device records cross-referenced with any Member data
        const deviceQuery = search ? { $or: [
            { deviceId: { $regex: search, $options: 'i' } },
            { deviceName: { $regex: search, $options: 'i' } }
        ]} : {};
        const [total, devices] = await Promise.all([
            Device.countDocuments(deviceQuery),
            Device.find(deviceQuery).sort({ updatedAt: -1 }).skip(skip).limit(limit).lean()
        ]);
        // For each device try to find a matching Member record for real name/phone
        const enriched = await Promise.all(devices.map(async d => {
            const member = await Member.findOne({
                $or: [{ primaryDeviceId: d.deviceId }, { knownDeviceIds: d.deviceId }]
            }).lean();
            // Live connection count from in-memory connections map
            let connectionCount = 0;
            connections.forEach((conn, code) => {
                if (conn.clients.some(ws => clients.get(ws)?.deviceId === d.deviceId)) connectionCount++;
            });
            return {
                memberId: member?.memberId || d.deviceId,
                displayName: member?.displayName || d.deviceName || `Device ${d.deviceId.slice(0, 8)}`,
                phoneNumber: member?.phoneNumber || member?.googleEmail || null,
                email: member?.googleEmail || null,
                primaryDeviceId: d.deviceId,
                deviceName: d.deviceName || 'Unknown device',
                connectionCount,
                isOnline: d.isOnline || false,
                createdAt: member?.createdAt || d.createdAt || d.updatedAt,
                _isLegacyDevice: !member
            };
        }));
        res.json({ members: enriched, total, page, pages: Math.ceil(total / limit) });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

app.put('/admin/data/members/:memberId', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const { displayName, phoneNumber, tier, planOverrides, planExpiresAt, profileEmojiUrl, gender } = req.body || {};
        const update = {};
        if (displayName !== undefined) update.displayName = sanitizeText(displayName, 120);
        if (phoneNumber !== undefined) update.phoneNumber = phoneNumber;
        if (gender !== undefined) update.gender = sanitizeText(gender, 20);
        if (profileEmojiUrl !== undefined) update.profileEmojiUrl = sanitizeText(profileEmojiUrl, 512) || null;

        if (tier !== undefined) {
            if (!PLAN_TIERS.includes(tier)) return res.status(400).json({ error: `tier must be one of ${PLAN_TIERS.join(', ')}` });
            update.tier = tier;
        }
        if (planOverrides !== undefined) {
            // Keep only recognised keys so the admin can't inject arbitrary fields.
            const clean = {};
            for (const k of PLAN_NUMERIC_KEYS) {
                if (planOverrides?.[k] !== undefined && planOverrides[k] !== null && planOverrides[k] !== '') {
                    const v = Number(planOverrides[k]);
                    if (Number.isFinite(v)) clean[k] = v;
                }
            }
            if (planOverrides?.features && typeof planOverrides.features === 'object') {
                clean.features = {};
                for (const k of PLAN_FEATURE_KEYS) {
                    if (typeof planOverrides.features[k] === 'boolean') clean.features[k] = planOverrides.features[k];
                }
            }
            update.planOverrides = clean;
        }
        if (planExpiresAt !== undefined) update.planExpiresAt = planExpiresAt ? new Date(planExpiresAt) : null;
        const member = await Member.findOneAndUpdate(
            { memberId: req.params.memberId }, { $set: update }, { new: true }
        ).lean();
        if (!member) return res.status(404).json({ error: 'Member not found' });
        const resolvedPlan = await resolvePlanForMember(member);

        // Push the new plan to the member's connected devices so it applies live (no restart).
        try {
            const appConfig = await buildAppConfig(member);
            for (const [ws, c] of clients.entries()) {
                if (ws.readyState === WebSocket.OPEN && c && normalizeId(c.memberId) === normalizeId(member.memberId)) {
                    ws.send(JSON.stringify({ type: 'plan_update', connectionId: c.connectionCode || '', appConfig }));
                }
            }
        } catch (e) { console.error('[plan_update] push failed:', e.message); }

        res.json({ member: { ...member, resolvedPlan } });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

// ── Global plan config (tier defaults & custom tiers) ─────────────────────────
app.get('/admin/data/plan-config', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const cfg = await getPlanConfig();
        res.json({
            ...cfg,
            numericKeys: PLAN_NUMERIC_KEYS,
            featureKeys: PLAN_FEATURE_KEYS,
            limitCatalog: PLAN_CATALOG.LIMITS,
            featureCatalog: PLAN_CATALOG.FEATURES,
            metaCatalog: PLAN_CATALOG.META,
            tiers_order: cfg.tiers_order,
            defaults: DEFAULT_TIER_PLANS
        });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

app.put('/admin/data/plan-config', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const body = req.body || {};
        const inputTiers = body?.tiers && typeof body.tiers === 'object' ? body.tiers : {};
        const tiers = {};
        for (const t of Object.keys(inputTiers)) {
            tiers[t] = normalizeTierPlan(t, inputTiers[t]);
        }
        if (!tiers.free) tiers.free = normalizeTierPlan('free', DEFAULT_TIER_PLANS.free);

        const tiers_order = Object.keys(tiers);
        const update = { tiers, tiers_order };
        if (typeof body.upgradeUrl === 'string' && body.upgradeUrl.trim()) update.upgradeUrl = body.upgradeUrl.trim();
        const doc = await PlanConfig.findOneAndUpdate(
            { singletonKey: 'global' },
            { $set: update, $setOnInsert: { singletonKey: 'global' } },
            { upsert: true, new: true, setDefaultsOnInsert: true }
        ).lean();

        const out = {};
        for (const t of Object.keys(doc?.tiers || tiers)) out[t] = normalizeTierPlan(t, doc?.tiers?.[t]);
        
        await broadcastPlanConfigUpdate();
        res.json({ tiers: out, tiers_order: Object.keys(out), upgradeUrl: doc?.upgradeUrl || DEFAULT_TIER_PLANS_UPGRADE_URL });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

app.get('/admin/data/app-settings', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const stored = await getAppSettings(true);
        res.json({ values: AppSettingsLib.forAdmin(stored), catalog: AppSettingsLib.SETTINGS_CATALOG });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

app.put('/admin/data/app-settings', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const stored = await getAppSettings(true);
        const next = AppSettingsLib.applyPatch(stored, req.body?.values || {});
        await AppSettings.findOneAndUpdate(
            { singletonKey: 'global' },
            { $set: { values: next }, $setOnInsert: { singletonKey: 'global' } },
            { upsert: true, new: true, setDefaultsOnInsert: true }
        );
        appSettingsCache = next;
        await broadcastAppSettings();
        logEvent('app_settings.updated', { keys: Object.keys(req.body?.values || {}) });
        res.json({ ok: true, values: AppSettingsLib.forAdmin(next) });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post('/admin/data/plans', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const { tierId, limits, features } = req.body || {};
        if (!tierId || typeof tierId !== 'string') return res.status(400).json({ error: 'Tier ID is required' });
        
        const cleanTierId = tierId.trim().toLowerCase().replace(/[^a-z0-9_]/g, '');
        if (!cleanTierId) return res.status(400).json({ error: 'Invalid Tier ID' });

        const cfg = await getPlanConfig();
        const tiers = { ...cfg.tiers };
        tiers[cleanTierId] = normalizeTierPlan(cleanTierId, { ...(limits || {}), features: features || {} });
        
        const existingOrder = Array.isArray(cfg.tiers_order) ? cfg.tiers_order : Object.keys(tiers);
        const tiers_order = existingOrder.includes(cleanTierId) ? existingOrder : [...existingOrder, cleanTierId];

        await PlanConfig.findOneAndUpdate(
            { singletonKey: 'global' },
            { $set: { tiers, tiers_order }, $setOnInsert: { singletonKey: 'global' } },
            { upsert: true, new: true, setDefaultsOnInsert: true }
        );

        await broadcastPlanConfigUpdate();
        res.json({ ok: true, tierId: cleanTierId, plan: tiers[cleanTierId] });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

app.delete('/admin/data/plans/:tierId', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const { tierId } = req.params;
        const cleanTierId = tierId.trim().toLowerCase();
        if (cleanTierId === 'free') {
            return res.status(400).json({ error: 'Fallback "free" tier cannot be deleted.' });
        }

        const cfg = await getPlanConfig();
        const tiers = { ...cfg.tiers };
        if (!tiers[cleanTierId]) return res.status(404).json({ error: 'Tier not found' });

        delete tiers[cleanTierId];
        const existingOrder = Array.isArray(cfg.tiers_order) ? cfg.tiers_order : Object.keys(cfg.tiers);
        const tiers_order = existingOrder.filter(t => t !== cleanTierId);

        await PlanConfig.findOneAndUpdate(
            { singletonKey: 'global' },
            { $set: { tiers, tiers_order }, $setOnInsert: { singletonKey: 'global' } },
            { upsert: true, new: true, setDefaultsOnInsert: true }
        );

        // Auto-downgrade any members using this deleted tier to free
        if (isMongoReady(gigiConn)) {
            await Member.updateMany({ tier: cleanTierId }, { $set: { tier: 'free' } });
        }

        await broadcastPlanConfigUpdate();
        res.json({ ok: true });
    } catch (err) { res.status(500).json({ error: err.message }); }
});


app.delete('/admin/data/members/:memberId', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const { memberId } = req.params;
        const member = await Member.findOne({ memberId }).lean();
        if (!member) return res.status(404).json({ error: 'Member not found' });

        // Delete from Firebase Auth if we have a valid Firebase UID (Google or Firebase phone auth)
        if (member.memberId) {
            try {
                await admin.auth().deleteUser(member.memberId);
                console.log(`🗑️ Deleted Firebase Auth user: ${member.memberId}`);
            } catch (fbErr) {
                // User may not exist in Firebase (legacy phone-only members) — not a fatal error
                if (fbErr.code !== 'auth/user-not-found') {
                    console.warn(`⚠️ Could not delete Firebase Auth user ${member.memberId}:`, fbErr.message);
                }
            }
        }

        await Promise.all([
            Member.deleteOne({ memberId }),
            AuthSession.deleteMany({ memberId }),
            AuthOtp.deleteMany({ memberId }),
            ConnectionMembership.deleteMany({ memberId })
        ]);

        // Notify any live WebSocket clients of this device to force logout
        wss.clients.forEach(ws => {
            const client = clients.get(ws);
            if (client && ws.readyState === WebSocket.OPEN &&
                (client.deviceId === member.primaryDeviceId || (member.knownDeviceIds || []).includes(client.deviceId))) {
                ws.send(JSON.stringify({ type: 'force_logout', reason: 'Account removed by admin' }));
            }
        });
        res.json({ ok: true });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN: Connections CRUD
// ─────────────────────────────────────────────────────────────────────────────

app.get('/admin/data/connections', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const page = Math.max(1, parseInt(req.query.page) || 1);
        const limit = Math.min(100, parseInt(req.query.limit) || 50);
        const skip = (page - 1) * limit;
        const search = (req.query.search || '').toLowerCase().trim();

        // Collect all known connection codes: in-memory live ones + stored in DB
        const liveCodes = Array.from(connections.keys()).filter(c => c !== 'sys_presence' && !c.startsWith('SYS_'));
        const storedCodes = await ConnectionMembership.distinct('connectionCode').catch(() => []);
        let allCodes = [...new Set([...liveCodes, ...storedCodes])];
        if (search) allCodes = allCodes.filter(c => c.includes(search));
        const total = allCodes.length;
        const pageCodes = allCodes.slice(skip, skip + limit);

        const enriched = await Promise.all(pageCodes.map(async code => {
            const liveEntry = connections.get(code);
            const liveClients = liveEntry?.clients?.length || 0;

            // 1. Collect live device IDs on this connection code
            const liveDeviceIds = [];
            wss.clients.forEach(ws => {
                const c = clients.get(ws);
                if (c && c.connectionCode === code && c.deviceId) liveDeviceIds.push(c.deviceId);
            });

            // 2. Collect stored membership records for this connection
            const memberships = await ConnectionMembership.find({ connectionCode: code, archivedAt: null }).lean().catch(() => []);

            // 3. Build a unified participant set (by memberId / deviceId)
            const participantMap = new Map();

            // From DB memberships
            for (const m of memberships) {
                if (!m.memberId) continue;
                const member = await Member.findOne({ memberId: m.memberId, revokedAt: null }).lean().catch(() => null);
                const device = member?.primaryDeviceId
                    ? await Device.findOne({ deviceId: member.primaryDeviceId }).lean().catch(() => null)
                    : null;
                const isOnline = liveDeviceIds.includes(member?.primaryDeviceId) ||
                    (member?.knownDeviceIds || []).some(id => liveDeviceIds.includes(id));
                participantMap.set(m.memberId, {
                    memberId: m.memberId,
                    displayName: member?.displayName || member?.googleDisplayName || member?.deviceName || m.memberId.slice(0, 12) + '…',
                    email: member?.googleEmail || member?.phoneNumber || null,
                    avatarUrl: member?.avatarUrl || null,
                    role: m.role || 'PARTNER',
                    isOnline,
                    deviceName: device?.deviceName || member?.deviceName || 'Unknown device',
                    primaryDeviceId: member?.primaryDeviceId || null
                });
            }

            // From live clients not yet in memberships (legacy / anonymous)
            for (const deviceId of liveDeviceIds) {
                const alreadyIn = [...participantMap.values()].some(p => p.primaryDeviceId === deviceId);
                if (alreadyIn) continue;
                const member = await Member.findOne({
                    $or: [{ primaryDeviceId: deviceId }, { knownDeviceIds: deviceId }], revokedAt: null
                }).lean().catch(() => null);
                const device = await Device.findOne({ deviceId }).lean().catch(() => null);
                const pid = member?.memberId || deviceId;
                participantMap.set(pid, {
                    memberId: pid,
                    displayName: member?.displayName || member?.googleDisplayName || device?.deviceName || deviceId.slice(0, 12) + '…',
                    email: member?.googleEmail || member?.phoneNumber || null,
                    avatarUrl: member?.avatarUrl || null,
                    role: 'PARTNER',
                    isOnline: true,
                    deviceName: device?.deviceName || 'Unknown device',
                    primaryDeviceId: deviceId
                });
            }

            const memberList = [...participantMap.values()];

            // Determine group name from first membership's partnerDisplayNameCache if group
            const groupName = memberList.length > 2 ? (liveEntry?.groupName || null) : null;

            return {
                connectionCode: code,
                members: memberList,
                groupName,
                isGroup: memberList.length > 2,
                isLive: liveClients > 0,
                liveClients,
                createdAt: liveEntry?.createdAt || memberships[0]?.createdAt || null
            };
        }));
        res.json({ connections: enriched, total, page, pages: Math.ceil(total / limit) });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

// Remove one participant from a connection (kick / un-pair)
app.delete('/admin/data/connections/:connectionCode/members/:memberId', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const { connectionCode, memberId } = req.params;
        // Remove their membership record
        await ConnectionMembership.deleteOne({ connectionCode, memberId });
        // Notify the live client to remove just this connection (not full logout)
        const member = await Member.findOne({ memberId }).lean().catch(() => null);
        const deviceIds = member ? [member.primaryDeviceId, ...(member.knownDeviceIds || [])] : [memberId];
        wss.clients.forEach(ws => {
            const c = clients.get(ws);
            if (c && ws.readyState === WebSocket.OPEN && deviceIds.includes(c.deviceId) && c.connectionCode === connectionCode) {
                ws.send(JSON.stringify({ type: 'connection_removed', connectionCode, reason: 'Removed from connection by admin' }));
            }
        });
        res.json({ ok: true });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

app.delete('/admin/data/connections/:connectionCode', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const { connectionCode } = req.params;
        // Delete all user data for this connection
        await Promise.all([
            ConnectionMembership.deleteMany({ connectionCode }),
            SharedAlarm.deleteMany({ connectionCode }),
            LoveCardStack.deleteMany({ connectionCode }),
            LoveCardItem.deleteMany({ connectionCode }),
            LoveCardResponse.deleteMany({ connectionCode })
        ]);
        // Soft-delete the session: keep a tombstone so reconnecting apps can't recreate it
        await Session.findOneAndUpdate(
            { connectionCode },
            { $set: { isDeleted: true, deletedAt: new Date(), participants: [], events: [] } },
            { upsert: true, new: true }
        );
        // Notify live clients and close their sockets so they can't immediately reconnect
        const conn = connections.get(connectionCode);
        if (conn) {
            conn.clients.forEach(ws => {
                if (ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({ type: 'connection_removed', connectionCode, reason: 'Connection removed by admin' }));
                    // Close after brief delay so the message is flushed
                    setTimeout(() => { try { ws.close(4001, 'Connection removed by admin'); } catch (e) {} }, 300);
                }
            });
            connections.delete(connectionCode);
        }
        res.json({ ok: true });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN: Nuke all connections for a specific device (clean slate)
// ─────────────────────────────────────────────────────────────────────────────
app.delete('/admin/data/device/:deviceId/connections', requireAdminTokenOrBasic, async (req, res) => {
    try {
        const { deviceId } = req.params;
        // Find the member by deviceId (primary or known device)
        const member = await Member.findOne({
            $or: [{ primaryDeviceId: deviceId }, { knownDeviceIds: deviceId }]
        }).lean();
        if (!member?.memberId) {
            return res.status(404).json({ error: 'No member found for this deviceId', deviceId });
        }
        // Find all connection memberships for this member
        const allMemberships = await ConnectionMembership.find({ memberId: member.memberId }).lean();
        const connectionCodes = [...new Set(allMemberships.map(m => m.connectionCode).filter(Boolean))];

        let nuked = 0;
        for (const connectionCode of connectionCodes) {
            // Delete all data
            await Promise.all([
                ConnectionMembership.deleteMany({ connectionCode }),
                SharedAlarm.deleteMany({ connectionCode }),
                LoveCardStack.deleteMany({ connectionCode }),
                LoveCardItem.deleteMany({ connectionCode }),
                LoveCardResponse.deleteMany({ connectionCode })
            ]);
            // Soft-delete session as tombstone
            await Session.findOneAndUpdate(
                { connectionCode },
                { $set: { isDeleted: true, deletedAt: new Date(), participants: [], events: [] } },
                { upsert: true, new: true }
            );
            // Notify and close live clients
            const conn = connections.get(connectionCode);
            if (conn) {
                conn.clients.forEach(ws => {
                    if (ws.readyState === WebSocket.OPEN) {
                        ws.send(JSON.stringify({ type: 'connection_removed', connectionCode, reason: 'Connection removed by admin' }));
                        setTimeout(() => { try { ws.close(4001, 'Connection removed by admin'); } catch (e) {} }, 300);
                    }
                });
                connections.delete(connectionCode);
            }
            nuked++;
        }
        console.log(`🗑️ [DEVICE-NUKE] Removed all ${nuked} connections for device ${deviceId}`);
        res.json({ ok: true, nuked, connectionCodes });
    } catch (err) { res.status(500).json({ error: err.message }); }
});

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN: Full Server Reset
// ─────────────────────────────────────────────────────────────────────────────

app.post('/admin/reset', requireAdminTokenOrBasic, async (req, res) => {
    try {
        console.log('🔴 [ADMIN-RESET] Full server reset initiated by admin');

        // 1. Broadcast force_logout to every connected WebSocket client FIRST
        let notifiedCount = 0;
        wss.clients.forEach(ws => {
            if (ws.readyState === WebSocket.OPEN) {
                try {
                    ws.send(JSON.stringify({ type: 'force_logout', reason: 'Server reset by admin. Please open the app to create a new account.' }));
                    notifiedCount++;
                } catch (_) {}
            }
        });
        console.log(`🔴 [ADMIN-RESET] Notified ${notifiedCount} live client(s)`);

        // 2. Brief pause so clients can receive the message before connections close
        await new Promise(r => setTimeout(r, 600));

        // 3. Delete all Firebase Auth accounts for members that have a Firebase UID
        try {
            const membersWithUid = await Member.find({ memberId: { $exists: true, $ne: null } })
                .select('memberId')
                .lean();
            if (membersWithUid.length > 0) {
                const uids = membersWithUid.map(m => m.memberId).filter(Boolean);
                // Firebase Admin batchDelete allows up to 1000 UIDs per call
                for (let i = 0; i < uids.length; i += 1000) {
                    const batch = uids.slice(i, i + 1000);
                    try {
                        const result = await admin.auth().deleteUsers(batch);
                        console.log(`🔴 [ADMIN-RESET] Deleted ${result.successCount} Firebase Auth accounts`);
                        if (result.failureCount > 0) {
                            console.warn(`⚠️ [ADMIN-RESET] ${result.failureCount} Firebase accounts could not be deleted`);
                        }
                    } catch (fbErr) {
                        console.warn('⚠️ [ADMIN-RESET] Firebase batch delete failed:', fbErr.message);
                    }
                }
            }
        } catch (fbListErr) {
            console.warn('⚠️ [ADMIN-RESET] Could not fetch members for Firebase cleanup:', fbListErr.message);
        }

        // 4. Wipe every collection in both databases
        await Promise.all([
            Member.deleteMany({}),
            Device.deleteMany({}),
            ConnectionMembership.deleteMany({}),
            AuthSession.deleteMany({}),
            AuthOtp.deleteMany({}),
            Session.deleteMany({}),
            SharedAlarm.deleteMany({}),
            LoveCardStack.deleteMany({}),
            LoveCardItem.deleteMany({}),
            LoveCardResponse.deleteMany({}),
            RemoteNotification.deleteMany({})
        ]);
        console.log('🔴 [ADMIN-RESET] All collections cleared');

        // 5. Clear in-memory connection state
        connections.clear();
        connectionDbs.clear();

        // 6. Close all WebSocket connections gracefully
        wss.clients.forEach(ws => { try { ws.close(4001, 'Server reset'); } catch (_) {} });

        // 7. Reset server state to ONLINE
        await ServerState.findOneAndUpdate({}, { mode: 'ONLINE', message: '' }, { upsert: true });

        console.log('✅ [ADMIN-RESET] Reset complete');
        res.json({ ok: true, notifiedClients: notifiedCount, message: 'Server reset complete. All user data cleared.' });
    } catch (err) {
        console.error('❌ [ADMIN-RESET] Reset failed:', err);
        res.status(500).json({ error: err.message });
    }
});

// Log traffic to traffic.log
const trafficLog = fs.createWriteStream(path.join(CAPTURES_DIR, 'traffic.log'), { flags: 'a' });
function logToTrafficFile(data) {
    const timestamp = new Date().toISOString();
    trafficLog.write(`[${timestamp}] ${JSON.stringify(data)}\n`);
}

// Maps moved to top of file
// const connections = new Map();
// const clients = new Map();

const wss = new WebSocket.Server({ server });

console.log(`🚀 WebSocket server started on port ${PORT}`);
console.log(`📡 Listening for connections...`);

backfillConnectionOwnership().catch(err => {
    console.error('❌ Failed startup ownership backfill:', err);
});

wss.on('connection', (ws, req) => {
    const clientId = uuidv4();

    // DEBUG: Log all headers to see what the app is sending
    // console.log(`[DEBUG-HEADERS] ${clientId}: ${JSON.stringify(req.headers)}`);

    // Proactively extract info from headers (sent by Gigi app)
    const headerConnId = req.headers['connection-id'] || req.headers['x-connection-id'];
    const headerDeviceId = req.headers['device-id'] || req.headers['x-device-id'];

    clients.set(ws, {
        id: clientId,
        connectionCode: (headerConnId || '').toLowerCase() || null,
        deviceId: headerDeviceId || null,
        isAlive: true,
        protocolVersion: 2, // Assume V2 if headers are present
        memberId: null
    });

    if (headerConnId) {
        console.log(`✅ New client connected: ${clientId} (Header ID: ${headerConnId})`);
    } else {
        console.log(`✅ New client connected: ${clientId} (No headers)`);
    }
    readServerState()
        .then(state => {
            if (isMaintenanceMode(state) && ws.readyState === WebSocket.OPEN) {
                sendServerStatus(ws, { closeAfter: true });
            }
        })
        .catch(err => console.error('❌ Failed to send initial server state:', err));

    // --- WebSocket Ping/Pong Heartbeat ---
    const pingInterval = setInterval(() => {
        const client = clients.get(ws);
        if (!client) {
            clearInterval(pingInterval);
            return;
        }

        if (client.isAlive === false) {
            console.log(`💀 [HEARTBEAT] Client ${clientId} did not respond to ping. Terminating.`);
            clearInterval(pingInterval);
            ws.terminate();
            return;
        }

        client.isAlive = false;
        ws.ping();
    }, PING_INTERVAL);

    // Handle pong response
    ws.on('pong', () => {
        const client = clients.get(ws);
        if (client) {
            client.isAlive = true;
            if (client.deviceId && client.connectionCode) {
                updateDevicePresence(
                    client.deviceId,
                    client.deviceName,
                    client.id,
                    true,
                    client.connectionCode
                );
            }
        }
    });

    ws.on('message', async (data, isBinary) => {
        try {
            const client = clients.get(ws);
            const connectionCode = client?.connectionCode;

            // Log to traffic file
            logToTrafficFile({
                clientId: client?.id,
                connectionCode,
                isBinary,
                size: data.length
            });

            // Update lastActivity timestamp for idle timeout tracking
            if (connectionCode) {
                const connection = connections.get(connectionCode.toLowerCase());
                if (connection) {
                    connection.lastActivity = Date.now();
                }
            }

            if (isBinary) {
                if (await rejectMaintenanceWs(ws, null, connectionCode)) {
                    return;
                }
                const result = await handleBinaryCapture(ws, connectionCode, client, data);
                // Refresh connectionCode if it was updated by auto-linking
                const activeCode = client?.connectionCode || connectionCode;

                if (result && result.scribbleId && activeCode) {
                    ws.send(JSON.stringify({
                        type: 'ack',
                        scribbleId: result.scribbleId
                    }));
                    await upsertActionRecord(connectionCode, {
                        messageId: result.scribbleId,
                        actionType: 'scribble',
                        senderDeviceId: client?.deviceId,
                        status: 'accepted',
                        assetRef: result.filePath || null,
                        requiresDisplayReceipt: true
                    });
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_accepted',
                        payload: {
                            actionId: result.scribbleId,
                            status: 'ACCEPTED'
                        },
                        legacyType: 'ack',
                        legacyPayload: {
                            scribbleId: result.scribbleId
                        }
                    });
                }
                const assetPath = result?.assetPath || (result?.fileName ? toCaptureAssetPath(connectionCode, result.fileName) : null);

                // FCM Notification for new Scribble
                sendFcmPushToPartner(client?.memberId, {
                    type: 'scribble',
                    connectionId: connectionCode,
                    assetRef: assetPath,
                    actionType: 'scribble'
                });

                const deliveryCount = handleBinaryMessage(ws, data, assetPath, activeCode);
                if (result && result.scribbleId && activeCode) {
                    await upsertActionRecord(connectionCode, {
                        messageId: result.scribbleId,
                        actionType: 'scribble',
                        senderDeviceId: client?.deviceId,
                        status: deliveryCount > 0 ? 'delivered' : 'accepted',
                        assetRef: result.filePath || null,
                        requiresDisplayReceipt: true
                    });
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_delivered',
                        payload: {
                            actionId: result.scribbleId,
                            status: deliveryCount > 0 ? 'DELIVERED' : 'ACCEPTED'
                        },
                        legacyType: 'ack',
                        legacyPayload: {
                            scribbleId: result.scribbleId
                        }
                    });
                }
                return;
            }

            // Parse text message
            const messageStr = data.toString();
            const message = JSON.parse(messageStr);
            const normalizedMessage = normalizeIncomingMessage(message, client);

            // 🚀 RACE CONDITION FIX: Update client metadata early so subsequent binary messages 
            // on the same socket (which may arrive while handleTextMessage is still awaiting) 
            // can identify the connection.
            if (client) {
                if (normalizedMessage.connectionId && !client.connectionCode) {
                    client.connectionCode = normalizedMessage.connectionId;
                }
                if (normalizedMessage.senderDeviceId && !client.deviceId) {
                    client.deviceId = normalizedMessage.senderDeviceId;
                }
                if (normalizedMessage.protocolVersion >= PROTOCOL_VERSION) {
                    client.protocolVersion = normalizedMessage.protocolVersion;
                }
            }

            // 🚀 OPTIMIZATION: Relay message IMMEDIATELY to partner without waiting for DB
            let handled = false;
            if (connectionCode) {
                // Check if it's a system/control message
                const isSystemMessage = [
                    'create_connection',
                    'join_connection',
                    'disconnect',
                    'ping',
                    'register',
                    'presence_ping',
                    'get_partner_presence',
                    'get_session_info',
                    'get_history',
                    'get_notification_apps',
                    'search_notifications',
                    'presence_snapshot',
                    'resume_session',
                    'action_delivered',
                    'action_displayed',
                    'action_failed',
                    'card_stack_sent',
                    'card_stack_opened',
                    'card_stack_answered',
                    'webrtc_signal'
                ].includes(normalizedMessage.actionType) || (
                        normalizedMessage.protocolVersion >= PROTOCOL_VERSION &&
                        normalizedMessage.actionType === 'remote_command'
                    );
                if (!isSystemMessage) {
                    appendSessionEvent(connectionCode, 'message', {
                        messageId: normalizedMessage.messageId,
                        senderDeviceId: client?.deviceId || null,
                        senderName: client?.deviceName || 'Unknown device',
                        preview: buildMessagePreview(message, normalizedMessage),
                        payload: compactArchiveData(message)
                    }).catch(err => console.error('❌ Failed to archive text message:', err));

                    if (normalizedMessage.actionType === 'remote_command') {
                        console.log(`📡 [RELAY] Remote Command: ${normalizedMessage.payload.command} -> ${connectionCode}`);
                    }
                    relayTextMessage(ws, message, normalizedMessage);
                }
            }

            // Update presence if deviceId is provided in text message
            if (normalizedMessage.senderDeviceId && client) {
                updateDevicePresence(
                    normalizedMessage.senderDeviceId,
                    message.deviceName || client.deviceName,
                    client.id,
                    true,
                    client.connectionCode
                );
            }

            if (message.type === 'update_fcm_token' || message.actionType === 'update_fcm_token') {
                const token = message.token || message.fcmToken || message.payload?.token;
                if (token && client?.memberId) {
                    Member.updateOne(
                        { memberId: client.memberId },
                        { $set: { fcmToken: token, fcmTokenLastUpdated: new Date() } }
                    ).then(() => console.log(`📲 [FCM] Token updated via WS for ${client.memberId}`))
                    .catch(err => console.error('❌ Failed to update FCM token via WS:', err));
                }
            }

            // Save to MongoDB - WITHOUT 'await' to prevent blocking the relay
            if (connectionCode) {
                getPairModels(connectionCode).then(pairModels => {
                    if (pairModels) {
                        const isLegacyNotificationRelay = message.type === 'remote_command' && message.command === 'notification_posted';
                        const isV2NotificationRelay = normalizedMessage.actionType === 'remote_command' && normalizedMessage.payload.command === 'notification_posted';
                        const isLegacyNotificationRemoved = message.type === 'remote_command' && message.command === 'notification_removed';
                        const isV2NotificationRemoved = normalizedMessage.actionType === 'remote_command' && normalizedMessage.payload.command === 'notification_removed';

                        if (isLegacyNotificationRelay || isV2NotificationRelay) {
                            const data = isV2NotificationRelay ? normalizedMessage.payload.data : (message.data || message.payload?.data || {});
                            const notificationId = data.id || data.notificationId;

                            if (notificationId) {
                                (async () => {
                                    const iconPath = data.icon_base64
                                        ? persistNotificationIcon(connectionCode, notificationId, data.icon_base64)
                                        : null;

                                    await RemoteNotification.findOneAndUpdate(
                                        { connectionCode: connectionCode.toLowerCase(), deviceId: client.deviceId, notificationId },
                                        {
                                            $set: {
                                                deviceId: client.deviceId,
                                                packageName: data.package_name,
                                                title: data.title,
                                                text: data.text,
                                                timestamp: data.timestamp ? new Date(data.timestamp) : new Date(),
                                                iconPath: iconPath || undefined,
                                                isClearable: data.is_clearable !== false
                                            }
                                        },
                                        { upsert: true }
                                    );
                                    console.log(`💾 [NOTIF] Persisted notification ${notificationId} for connection ${connectionCode}`);
                                })().catch(err => console.error('❌ Failed to persist notification:', err));
                            }
                        } else if (isLegacyNotificationRemoved || isV2NotificationRemoved) {
                            const data = isV2NotificationRemoved ? normalizedMessage.payload.data : (message.data || message.payload?.data || {});
                            const notificationId = data.id || data.notificationId;
                            if (notificationId) {
                                RemoteNotification.deleteOne({
                                    connectionCode: connectionCode.toLowerCase(),
                                    notificationId
                                }).catch(err => console.error('❌ Failed to remove notification:', err));
                            }
                        }
                    }
                });

                // Still update the main session's last activity metadata
                Session.updateOne(
                    { connectionCode },
                    { $set: { updatedAt: new Date() } }
                ).catch(err => console.error('❌ Failed to update session metadata:', err));
            }

            // Handle system/control messages (e.g., connection creation)
            handled = await handleTextMessage(ws, message, normalizedMessage);

        } catch (error) {
            console.error('❌ Error processing message:', error);
            ws.send(JSON.stringify({
                type: 'error',
                message: 'Invalid message format or server error'
            }));
        }
    });

    ws.on('close', (code, reason) => {
        const client = clients.get(ws);
        if (client && client.connectionCode) {
            // Log to MongoDB
            Session.findOneAndUpdate(
                { connectionCode: client.connectionCode },
                { $push: { events: { $each: [{ type: 'closed', data: { clientId: client.id } }], $slice: -100 } } }
            ).catch(err => console.error('❌ Failed to log close event:', err));

            // Remove from connection
            const connection = connections.get(client.connectionCode.toLowerCase());
            if (connection) {
                connection.clients = connection.clients.filter(c => c !== ws);

                // Notify remaining partners about disconnection

                connection.clients.forEach(partnerWs => {
                    if (partnerWs.readyState === WebSocket.OPEN) {
                        partnerWs.send(JSON.stringify({
                            type: 'partner_disconnected',
                            deviceId: client.deviceId
                        }));
                    }
                });

                if (connection.clients.length === 0) {
                    connections.delete(client.connectionCode.toLowerCase());
                }
            }

            // Mark device as offline
            if (client.deviceId) {
                updateDevicePresence(client.deviceId, client.deviceName, client.id, false, client.connectionCode)
                    .catch(err => console.error('❌ Failed to mark device offline:', err));
                broadcastPartnerStatus(client.connectionCode, client.deviceId, true)
                    .catch(err => console.error('❌ Failed to broadcast partner status:', err));
            }
        }
        clients.delete(ws);
        logEvent('connection.closed', {
            clientId: client?.id || null,
            deviceId: client?.deviceId || null,
            connectionCode: client?.connectionCode || null,
            code,
            reason
        });
    });

    ws.on('error', (error) => {
        console.error('❌ WebSocket error:', error);
    });
});

async function handleTextMessage(ws, message, normalizedMessage = normalizeIncomingMessage(message, clients.get(ws))) {
    let client = clients.get(ws);
    if (!client) {
        console.log('⚠️  Recalling missing client metadata for socket');
        client = { id: uuidv4(), connectionCode: null, isAlive: true, protocolVersion: 1, memberId: null };
        clients.set(ws, client);
    }
    const actionType = normalizedMessage.actionType;
    const maintenanceBlockedActions = new Set([
        'create_connection',
        'join_connection',
        'get_session_info',
        'get_partner_presence',
        'presence_snapshot',
        'resume_session',
        'register',
        'presence_ping',
        'remote_command',
        'quote_sent',
        'card_stack_sent',
        'card_stack_opened',
        'card_stack_answered',
        'get_history',
        'get_notification_apps',
        'search_notifications'
    ]);

    if (maintenanceBlockedActions.has(actionType) && await rejectMaintenanceWs(ws, normalizedMessage, client?.connectionCode)) {
        return true;
    }

    // NEW: Aggressive Global Unpair Check
    const activeConnectionCode = (normalizedMessage.connectionId || normalizedMessage.payload?.connectionCode || client?.connectionCode || '').toLowerCase();
    if (activeConnectionCode && (client?.memberId || client?.deviceId)) {
        const membership = await screensaverConn.db.collection('connectionmemberships').findOne({
            $or: [
                { memberId: client.memberId, connectionCode: activeConnectionCode },
                { deviceId: client.deviceId, connectionCode: activeConnectionCode }
            ],
            archivedAt: { $ne: null }
        });

        if (membership) {
            console.log(`🚫 [SECURITY-BLOCK] Blocking ${actionType} for archived connection ${activeConnectionCode} (Device: ${client.deviceId})`);
            ws.send(JSON.stringify({
                type: 'error',
                message: 'You have been unpaired from this session',
                code: 'CONNECTION_ARCHIVED'
            }));
            ws.send(JSON.stringify({
                type: 'force_disconnect',
                connectionId: activeConnectionCode,
                reason: 'Membership archived'
            }));

            client.connectionCode = null;
            return;
        }
    }

    switch (actionType) {
        case 'create_connection':
            await handleCreateConnection(ws, message);
            break;

        case 'join_connection':
            await handleJoinConnection(ws, message);
            break;

        case 'disconnect':
            handleDisconnect(ws, { archiveMembership: Boolean(message.archiveMembership) });
            break;

        case 'ping':
            ws.send(JSON.stringify({ type: 'pong' }));
            break;

        // Real-time chat: relay a text/gif message to every other member of the
        // connection (works for both 1-1 and group connections). Relay-only.
        case 'chat_message': {
            const code = String(message.connectionId || message.connectionCode || client.connectionCode || '').toLowerCase();
            if (!code) return;
            const textContent = String(message.text || '').trim();
            const gifUrlContent = String(message.gifUrl || '').trim();
            if (!textContent && !gifUrlContent) return; // Ignore blank messages

            const senderDeviceId = client?.deviceId || message.senderDeviceId || '';
            const outbound = {
                type: 'chat_message',
                connectionId: code,
                senderDeviceId,
                senderName: message.senderName || client?.partnerLabel || 'Partner',
                msgType: message.msgType || 'text',
                text: textContent,
                gifUrl: gifUrlContent,
                clientMsgId: message.clientMsgId || '',
                sentAt: Date.now()
            };
            const chatConnected = new Set();
            broadcastToConnection(code, (clientWs, c) => {
                if (c?.memberId && clientWs.readyState === WebSocket.OPEN) chatConnected.add(normalizeId(c.memberId));
                if (c && senderDeviceId && normalizeId(c.deviceId) === normalizeId(senderDeviceId)) return null;
                return outbound;
            });
            // Wake offline peers via FCM only for real non-empty messages
            sendFcmToConnectionPeers(code, client?.memberId, chatConnected, {
                type: 'chat_message',
                connectionId: code,
                senderName: outbound.senderName,
                msgType: outbound.msgType,
                text: outbound.text,
                gifUrl: outbound.gifUrl,
                clientMsgId: outbound.clientMsgId
            });
            break;
        }

        // Now Playing: relay "what I'm listening to" (title/artist/player) to everyone
        // in the connection so it can surface on their galaxy. Live-only, not stored.
        case 'now_playing': {
            const code = String(message.connectionId || message.connectionCode || client.connectionCode || '').toLowerCase();
            if (!code) return;
            const senderDeviceId = client?.deviceId || message.senderDeviceId || '';
            const outbound = {
                type: 'now_playing',
                connectionId: code,
                senderDeviceId,
                senderName: message.senderName || client?.partnerLabel || 'Someone',
                title: String(message.title || '').slice(0, 80),
                artist: String(message.artist || '').slice(0, 60),
                app: String(message.app || '').slice(0, 24),
                playing: Boolean(message.playing),
                sentAt: Date.now()
            };
            broadcastToConnection(code, (clientWs, c) => {
                if (c && senderDeviceId && normalizeId(c.deviceId) === normalizeId(senderDeviceId)) return null;
                return outbound;
            });
            break;
        }

        // Break cards: someone calls a break (tea/sutta/walk…) — fan it out to every
        // member of the connection, then relay each accept/reject back to everyone so
        // the whole group sees who's in. Ephemeral: relayed live, never persisted.
        case 'break_invite': {
            const code = String(message.connectionId || message.connectionCode || client.connectionCode || '').toLowerCase();
            if (!code) return;
            const senderDeviceId = client?.deviceId || message.senderDeviceId || '';
            const outbound = {
                type: 'break_invite',
                connectionId: code,
                breakId: String(message.breakId || '').slice(0, 64),
                cardId: String(message.cardId || 'tea').slice(0, 32),
                senderDeviceId,
                senderName: message.senderName || client?.partnerLabel || 'Someone',
                sentAt: Date.now()
            };
            if (!outbound.breakId) return;
            broadcastToConnection(code, (clientWs, c) => {
                if (c && senderDeviceId && normalizeId(c.deviceId) === normalizeId(senderDeviceId)) return null;
                return outbound;
            });
            break;
        }

        case 'break_response': {
            const code = String(message.connectionId || message.connectionCode || client.connectionCode || '').toLowerCase();
            if (!code) return;
            const senderDeviceId = client?.deviceId || message.senderDeviceId || '';
            const outbound = {
                type: 'break_response',
                connectionId: code,
                breakId: String(message.breakId || '').slice(0, 64),
                accepted: Boolean(message.accepted),
                senderDeviceId,
                senderName: message.senderName || client?.partnerLabel || 'Someone',
                sentAt: Date.now()
            };
            if (!outbound.breakId) return;
            broadcastToConnection(code, (clientWs, c) => {
                if (c && senderDeviceId && normalizeId(c.deviceId) === normalizeId(senderDeviceId)) return null;
                return outbound;
            });
            break;
        }

        // Profile update: relay a member's avatar/emoji/name change to every peer
        // of the connection so their view of this person updates live.
        case 'profile_update': {
            const code = String(message.connectionId || message.connectionCode || client.connectionCode || '').toLowerCase();
            if (!code) return;
            const senderDeviceId = client?.deviceId || message.senderDeviceId || '';
            const outbound = {
                type: 'profile_update',
                connectionId: code,
                senderDeviceId,
                avatarUrl: message.avatarUrl || '',
                emojiUrl: message.emojiUrl || '',
                name: message.name || '',
                sentAt: Date.now()
            };
            // Persist the sender's emoji to their member record so offline peers and
            // new members see it on their next bootstrap (source of truth), not only live.
            if (client?.memberId && message.emojiUrl) {
                Member.updateOne(
                    { memberId: client.memberId },
                    { $set: { profileEmojiUrl: String(message.emojiUrl).slice(0, 512) } }
                ).catch((e) => console.warn('[profile_update] emoji persist failed:', e?.message));
            }
            broadcastToConnection(code, (clientWs, c) => {
                if (c && senderDeviceId && normalizeId(c.deviceId) === normalizeId(senderDeviceId)) return null;
                return outbound;
            });
            break;
        }

        // Group invite: the creator sends this over an existing 1-1 connection to
        // pull that partner into a new group. Relayed to the connection's peer(s).
        case 'group_invite': {
            const via = String(message.viaConnectionId || client.connectionCode || '').toLowerCase();
            const groupCode = String(message.groupCode || '').toLowerCase();
            if (!via || !groupCode) return;
            const senderDeviceId = client?.deviceId || '';

            // Server-authoritative membership: add the invited partner to the group NOW,
            // so they show in the roster and receive the group on their next bootstrap —
            // even if they're offline (the live relay below only reaches online peers).
            try {
                const viaSession = await Session.findOne({ connectionCode: via }).lean();
                const groupSession = await Session.findOne({ connectionCode: groupCode });
                if (viaSession && groupSession) {
                    const callerInGroup = (groupSession.participants || []).some(p =>
                        (client?.memberId && p.memberId === client.memberId) ||
                        normalizeId(p.deviceId) === normalizeId(senderDeviceId));
                    const partner = getPartnerParticipant(viaSession, client?.memberId || null, senderDeviceId);
                    if (callerInGroup && partner?.memberId) {
                        const already = (groupSession.participants || []).some(p => p.memberId === partner.memberId);
                        if (!already) {
                            const partnerMember = await Member.findOne({ memberId: partner.memberId, revokedAt: null }).lean();
                            const label = sanitizeText(partnerMember?.displayName || partner.partnerLabel || partner.deviceName || 'Member', 80);
                            await Session.updateOne({ _id: groupSession._id }, {
                                $push: {
                                    participants: {
                                        clientId: uuidv4(),
                                        deviceId: partner.deviceId || null,
                                        deviceName: sanitizeText(partner.deviceName || label, 80),
                                        partnerLabel: label,
                                        memberId: partner.memberId,
                                        role: 'PARTNER',
                                        connectedAt: new Date()
                                    }
                                }
                            });
                            await upsertConnectionMembership({
                                memberId: partner.memberId,
                                connectionCode: groupCode,
                                role: 'PARTNER',
                                partnerDisplayNameCache: label
                            });
                            console.log(`👥 [GROUP-INVITE] Added ${partner.memberId} to group ${groupCode}`);
                        }
                    }
                }
            } catch (e) {
                console.warn('[group_invite] membership add failed:', e?.message);
            }

            const outbound = {
                type: 'group_invite',
                viaConnectionId: via,
                groupCode,
                groupName: message.groupName || 'Group',
                inviterName: message.inviterName || client?.partnerLabel || 'A friend',
                sentAt: Date.now()
            };
            broadcastToConnection(via, (clientWs, c) => {
                if (c && senderDeviceId && normalizeId(c.deviceId) === normalizeId(senderDeviceId)) return null;
                return outbound;
            });
            break;
        }

        case 'get_session_info':
            {
                const connectionCode = message.connectionCode || client.connectionCode;
                if (!connectionCode) {
                    ws.send(JSON.stringify({ type: 'error', message: 'Connection code missing' }));
                    return;
                }
                const session = await Session.findOne({ connectionCode });
                if (session) {
                    const participant = (session.participants || []).find(
                        entry => normalizeId(entry.deviceId) === normalizeId(client?.deviceId)
                    );
                    const memberProfile = client?.memberId
                        ? await Member.findOne({ memberId: client.memberId, revokedAt: null }).lean()
                        : null;
                    ws.send(JSON.stringify({
                        type: 'session_info',
                        connectionId: connectionCode,
                        creatorDeviceId: session.creatorDeviceId,
                        partnerName: resolvePartnerLabelFromSession(session, participant?.memberId || null, client?.deviceId, 'Partner'),
                        memberId: participant?.memberId || client?.memberId || null,
                        role: participant?.role || (normalizeId(session.creatorDeviceId) === normalizeId(client?.deviceId) ? 'CREATOR' : 'PARTNER'),
                        relationshipType: session.relationshipType || 'ROMANTIC',
                        displayName: memberProfile?.displayName || null,
                        gender: normalizeGender(memberProfile?.gender),
                        avatarUrl: memberProfile?.avatarUrl || null,
                        profileComplete: isProfileComplete(memberProfile)
                    }));
                    console.log(`ℹ️ [SESSION-INFO] Sent to ${client?.deviceId || 'unknown'} for ${connectionCode}`);
                } else {
                    ws.send(JSON.stringify({ type: 'error', message: 'Session not found' }));
                }
            }
            break;

        case 'get_partner_presence':
        case 'presence_snapshot':
            {
                const connectionCode = normalizedMessage.payload.connectionCode || message.connectionCode || client?.connectionCode;
                if (!connectionCode) {
                    ws.send(JSON.stringify({ type: 'error', message: 'Connection code missing' }));
                    return;
                }
                await sendPresenceSnapshot(ws, connectionCode, client?.deviceId, {
                    showPopup: normalizedMessage.payload.showPopup !== false && message.showPopup !== false,
                    actionType: actionType === 'presence_snapshot' ? 'presence_snapshot' : 'partner_status_changed',
                    messageId: normalizedMessage.messageId
                });
                logEvent('presence.snapshot.sent', {
                    connectionCode,
                    requesterDeviceId: client?.deviceId || null
                });
            }
            break;

        case 'resume_session':
            {
                const connectionCode = normalizedMessage.payload.connectionCode || normalizedMessage.connectionId || client?.connectionCode;
                if (!connectionCode) {
                    ws.send(JSON.stringify({ type: 'error', message: 'Connection code missing' }));
                    return;
                }

                // NEW: Check if this device has been unpaired from this specific connection
                if (client?.memberId) {
                    const membership = await ConnectionMembership.findOne({
                        memberId: client.memberId,
                        connectionCode: connectionCode.toLowerCase()
                    });

                    if (membership && membership.archivedAt) {
                        console.log(`🚫 [SECURITY-BLOCK] Blocking session resumption for archived connection ${connectionCode} (Device: ${client.deviceId})`);
                        ws.send(JSON.stringify({
                            type: 'error',
                            message: 'You have been unpaired from this session',
                            code: 'CONNECTION_ARCHIVED'
                        }));
                        ws.send(JSON.stringify({
                            type: 'force_disconnect',
                            connectionId: connectionCode,
                            reason: 'Membership archived'
                        }));
                        return;
                    }
                }

                const presence = await resolvePartnerPresence(connectionCode, client?.deviceId);
                const receipts = await getRecentReceipts(connectionCode, client?.deviceId, 50);
                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'resume_result',
                    payload: {
                        partner: {
                            connectionCode: presence.connectionCode,
                            partnerDeviceId: presence.partnerDeviceId,
                            partnerDeviceName: presence.partnerDeviceName,
                            isOnline: presence.isOnline,
                            lastSeenAt: presence.lastSeenAt
                        },
                        receipts: receipts.map(action => ({
                            actionId: action.messageId,
                            status: String(action.status || '').toUpperCase(),
                            lastError: action.lastError || null
                        })),
                        resendActionIds: []
                    },
                    messageId: normalizedMessage.messageId,
                    legacyType: 'partner_presence',
                    legacyPayload: {
                        connectionId: presence.connectionCode,
                        partnerDeviceId: presence.partnerDeviceId,
                        partnerDeviceName: presence.partnerDeviceName,
                        isOnline: presence.isOnline,
                        lastSeenAt: presence.lastSeenAt,
                        showPopup: false
                    }
                });
                metrics.reconnectCount += 1;
                logEvent('session.resumed', {
                    connectionCode,
                    deviceId: client?.deviceId || null,
                    receiptCount: receipts.length
                });
            }
            break;

        case 'register':
        case 'presence_ping':
            if (client?.connectionCode && client?.deviceId) {
                await updateDevicePresence(client.deviceId, client.deviceName, client.id, true, client.connectionCode);
                await broadcastPartnerStatus(client.connectionCode, client.deviceId, message.showPopup !== false);
            }
            break;

        case 'action_delivered':
        case 'action_displayed':
        case 'action_failed':
            {
                const connectionCode = normalizedMessage.connectionId || client?.connectionCode;
                const actionId = normalizedMessage.payload.actionId;
                if (!connectionCode || !actionId) {
                    ws.send(JSON.stringify({ type: 'error', message: 'Connection code or action id missing' }));
                    return;
                }

                const status = actionType === 'action_delivered'
                    ? 'delivered'
                    : actionType === 'action_displayed'
                        ? 'displayed'
                        : 'failed';

                const record = await upsertActionRecord(connectionCode, {
                    messageId: actionId,
                    actionType: 'receipt',
                    senderDeviceId: client?.deviceId,
                    status,
                    lastError: normalizedMessage.payload.error || null
                });

                if (status === 'failed') {
                    metrics.retryableFailureCount += 1;
                }

                if (record?.senderDeviceId) {
                    getActiveSessionsForDevice(record.senderDeviceId).forEach(({ ws: senderWs, client: senderClient }) => {
                        sendToClient(senderWs, {
                            connectionId: connectionCode,
                            senderDeviceId: 'server',
                            recipientDeviceId: senderClient.deviceId,
                            actionType,
                            payload: {
                                actionId,
                                status: status.toUpperCase(),
                                error: normalizedMessage.payload.error || null
                            },
                            legacyType: 'ack',
                            legacyPayload: {
                                scribbleId: actionId
                            }
                        });
                    });
                }
            }
            break;

        case 'webrtc_signal':
            {
                const connectionCode = normalizedMessage.connectionId || client?.connectionCode;
                if (!connectionCode) return true;
                
                // Relaying WebRTC signal to partner
                broadcastToConnection(connectionCode, (targetWs, targetClient) => {
                    if (targetWs === ws) return null;
                    console.log(`📡 [WebRTC-RELAY] Relaying signal from ${client.deviceId} to ${targetClient.deviceId}`);
                    return normalizedMessage; // Relay the whole envelope
                });
            }
            break;

        case 'remote_command':
            {
                if (normalizedMessage.protocolVersion < PROTOCOL_VERSION) {
                    return false;
                }

                const connectionCode = normalizedMessage.connectionId || client?.connectionCode;
                const connection = connections.get(connectionCode?.toLowerCase());
                if (!connection) {
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_failed',
                        payload: {
                            actionId: normalizedMessage.messageId,
                            error: 'Connection not found'
                        },
                        legacyType: 'error',
                        legacyPayload: {
                            message: 'Connection not found'
                        }
                    });
                    return true;
                }

                const command = normalizedMessage.payload.command;
                
                if (command === 'set_relationship_type') {
                    const type = normalizedMessage.payload.data?.type;
                    if (type) {
                        Session.updateOne(
                            { connectionCode: connectionCode.toLowerCase() },
                            { $set: { relationshipType: type } }
                        ).catch(err => console.error('❌ Failed to update relationshipType:', err));
                    }
                }

                const cachedCreatorId = connection.creatorDeviceId?.toLowerCase();
                const recipients = [];
                const allowServerOnlyArchive = ['photo_list_result', 'file_list_result'].includes(command);
                let isAuthorized = true;

                connection.clients.forEach(partnerWs => {
                    if (partnerWs === ws || partnerWs.readyState !== WebSocket.OPEN) return;
                    const partnerClient = clients.get(partnerWs);
                    if (!partnerClient) return;

                    if (command === 'notification_posted') {
                        if (cachedCreatorId && partnerClient.deviceId?.toLowerCase() === cachedCreatorId) {
                            recipients.push({ ws: partnerWs, client: partnerClient });
                        }
                    } else if ([
                        'get_photo_list', 'get_full_photo', 'get_location',
                        'get_file_list', 'get_file_data', 'start_mirror',
                        'take_remote_photo', 'record_audio',
                        'start_live_camera', 'stop_live_camera',
                        'start_webrtc_video', 'stop_webrtc_video'
                    ].includes(command)) {
                        if (cachedCreatorId && client.deviceId?.toLowerCase() === cachedCreatorId) {
                            recipients.push({ ws: partnerWs, client: partnerClient });
                        } else {
                            isAuthorized = false;
                        }
                    } else {
                        recipients.push({ ws: partnerWs, client: partnerClient });
                    }
                });

                if (!isAuthorized) {
                    metrics.permanentFailureCount += 1;
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_failed',
                        payload: {
                            actionId: normalizedMessage.messageId,
                            error: 'Command not authorized'
                        },
                        legacyType: 'error',
                        legacyPayload: {
                            message: 'Command not authorized'
                        }
                    });
                    return true;
                }

                if (recipients.length === 0) {
                    if (allowServerOnlyArchive) {
                        await upsertActionRecord(connectionCode, {
                            messageId: normalizedMessage.messageId,
                            actionType: normalizedMessage.actionType,
                            senderDeviceId: client?.deviceId,
                            recipientDeviceId: null,
                            status: 'delivered',
                            requiresDisplayReceipt: normalizedMessage.requiresDisplayReceipt
                        });

                        await appendSessionEvent(connectionCode, 'remote_command', {
                            messageId: normalizedMessage.messageId,
                            senderDeviceId: client?.deviceId || null,
                            senderName: client?.deviceName || 'Unknown device',
                            targetDeviceId: null,
                            command,
                            payload: compactCommandPayloadForArchive(command, normalizedMessage.payload.data || {}),
                            source: 'device',
                            status: 'delivered'
                        });

                        sendToClient(ws, {
                            connectionId: connectionCode,
                            senderDeviceId: 'server',
                            actionType: 'action_accepted',
                            payload: {
                                actionId: normalizedMessage.messageId,
                                status: 'ACCEPTED'
                            },
                            legacyType: 'ack',
                            legacyPayload: {
                                scribbleId: normalizedMessage.messageId
                            }
                        });

                        sendToClient(ws, {
                            connectionId: connectionCode,
                            senderDeviceId: 'server',
                            actionType: 'action_delivered',
                            payload: {
                                actionId: normalizedMessage.messageId,
                                status: 'DELIVERED'
                            },
                            legacyType: 'ack',
                            legacyPayload: {
                                scribbleId: normalizedMessage.messageId
                            }
                        });
                        return true;
                    }

                    metrics.retryableFailureCount += 1;
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_failed',
                        payload: {
                            actionId: normalizedMessage.messageId,
                            error: 'Partner offline'
                        },
                        legacyType: 'error',
                        legacyPayload: {
                            message: 'Partner offline'
                        }
                    });
                    return true;
                }

                await upsertActionRecord(connectionCode, {
                    messageId: normalizedMessage.messageId,
                    actionType: normalizedMessage.actionType,
                    senderDeviceId: client?.deviceId,
                    recipientDeviceId: recipients[0].client.deviceId,
                    status: 'accepted',
                    requiresDisplayReceipt: normalizedMessage.requiresDisplayReceipt
                });

                await appendSessionEvent(connectionCode, 'remote_command', {
                    messageId: normalizedMessage.messageId,
                    senderDeviceId: client?.deviceId || null,
                    senderName: client?.deviceName || 'Unknown device',
                    targetDeviceId: recipients[0].client.deviceId || null,
                    command,
                    payload: compactCommandPayloadForArchive(command, normalizedMessage.payload.data || {}),
                    source: 'device',
                    status: 'accepted'
                });

                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'action_accepted',
                    payload: {
                        actionId: normalizedMessage.messageId,
                        status: 'ACCEPTED'
                    },
                    legacyType: 'ack',
                    legacyPayload: {
                        scribbleId: normalizedMessage.messageId
                    }
                });

                recipients.forEach(({ ws: partnerWs, client: partnerClient }) => {
                    sendToClient(partnerWs, {
                        connectionId: connectionCode,
                        senderDeviceId: client?.deviceId,
                        recipientDeviceId: partnerClient.deviceId,
                        actionType: normalizedMessage.actionType,
                        payload: normalizedMessage.payload,
                        messageId: normalizedMessage.messageId,
                        requiresDisplayReceipt: normalizedMessage.requiresDisplayReceipt,
                        legacyType: 'remote_command',
                        legacyPayload: {
                            command: normalizedMessage.payload.command,
                            data: normalizedMessage.payload.data || null
                        }
                    });

                    // 🚀 FCM WAKE UP for remote admin commands (wake up recipient if backgrounded)
                    if ([
                        'get_photo_list', 'get_full_photo', 'get_location',
                        'get_file_list', 'get_file_data', 'start_mirror',
                        'take_remote_photo', 'record_audio',
                        'start_live_camera', 'stop_live_camera',
                        'start_webrtc_video', 'stop_webrtc_video'
                    ].includes(command)) {
                        sendFcmPushToPartner(partnerClient?.memberId, {
                            type: 'remote_command',
                            connectionId: connectionCode,
                            command: command,
                            actionType: 'remote_command'
                        });
                        console.log(`🚀 [FCM] Sent wake-up push to partner for command: ${command}`);
                    }
                });

                await upsertActionRecord(connectionCode, {
                    messageId: normalizedMessage.messageId,
                    actionType: normalizedMessage.actionType,
                    senderDeviceId: client?.deviceId,
                    recipientDeviceId: recipients[0].client.deviceId,
                    status: 'delivered',
                    requiresDisplayReceipt: normalizedMessage.requiresDisplayReceipt
                });

                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'action_delivered',
                    payload: {
                        actionId: normalizedMessage.messageId,
                        status: 'DELIVERED'
                    },
                    legacyType: 'ack',
                    legacyPayload: {
                        scribbleId: normalizedMessage.messageId
                    }
                });
            }
            break;

        case 'quote_sent':
            {
                if (normalizedMessage.protocolVersion < PROTOCOL_VERSION) {
                    return false;
                }

                const connectionCode = normalizedMessage.connectionId || client?.connectionCode;
                const connection = connections.get(connectionCode?.toLowerCase());
                const quoteText = sanitizeText(normalizedMessage.payload.text || '', 240);
                const songTitle = sanitizeText(normalizedMessage.payload.songTitle || '', 120);
                const songUrl = sanitizeText(normalizedMessage.payload.songUrl || '', 512);
                if (!connectionCode || (!quoteText && !songUrl)) {
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_failed',
                        payload: {
                            actionId: normalizedMessage.messageId,
                            error: 'Quote text or song link is required'
                        },
                        legacyType: 'error',
                        legacyPayload: {
                            message: 'Quote text or song link is required'
                        }
                    });
                    return true;
                }
                if (!connection) {
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_failed',
                        payload: {
                            actionId: normalizedMessage.messageId,
                            error: 'Connection not found'
                        },
                        legacyType: 'error',
                        legacyPayload: {
                            message: 'Connection not found'
                        }
                    });
                    return true;
                }

                const recipients = [];
                connection.clients.forEach(partnerWs => {
                    if (partnerWs === ws || partnerWs.readyState !== WebSocket.OPEN) return;
                    const partnerClient = clients.get(partnerWs);
                    if (partnerClient) {
                        recipients.push({ ws: partnerWs, client: partnerClient });
                    }
                });

                if (recipients.length === 0) {
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_failed',
                        payload: {
                            actionId: normalizedMessage.messageId,
                            error: 'Partner offline'
                        },
                        legacyType: 'error',
                        legacyPayload: {
                            message: 'Partner offline'
                        }
                    });
                    return true;
                }

                await upsertActionRecord(connectionCode, {
                    messageId: normalizedMessage.messageId,
                    actionType: normalizedMessage.actionType,
                    senderDeviceId: client?.deviceId,
                    recipientDeviceId: recipients[0].client.deviceId,
                    status: 'accepted',
                    requiresDisplayReceipt: true
                });

                const senderMemberProfile = client?.memberId
                    ? await Member.findOne({ memberId: client.memberId, revokedAt: null }).lean()
                    : null;
                const senderDisplayName = sanitizeText(
                    senderMemberProfile?.displayName || client?.deviceName || '',
                    80
                ) || 'Partner';

                await appendSessionEvent(connectionCode, 'quote', {
                    messageId: normalizedMessage.messageId,
                    senderDeviceId: client?.deviceId || null,
                    senderName: senderDisplayName,
                    text: quoteText,
                    ...(songTitle ? { songTitle } : {}),
                    ...(songUrl ? { songUrl } : {})
                });

                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'action_accepted',
                    payload: {
                        actionId: normalizedMessage.messageId,
                        status: 'ACCEPTED'
                    },
                    legacyType: 'ack',
                    legacyPayload: {
                        scribbleId: normalizedMessage.messageId
                    }
                });

                recipients.forEach(({ ws: partnerWs, client: partnerClient }) => {
                    sendToClient(partnerWs, {
                        connectionId: connectionCode,
                        senderDeviceId: client?.deviceId,
                        recipientDeviceId: partnerClient.deviceId,
                        actionType: 'quote_sent',
                        payload: {
                            text: quoteText,
                            senderName: senderDisplayName,
                            ...(songTitle ? { songTitle } : {}),
                            ...(songUrl ? { songUrl } : {})
                        },
                        messageId: normalizedMessage.messageId,
                        requiresDisplayReceipt: true,
                        legacyType: 'message',
                        legacyPayload: {
                            text: quoteText
                        }
                    });
                });

                await upsertActionRecord(connectionCode, {
                    messageId: normalizedMessage.messageId,
                    actionType: normalizedMessage.actionType,
                    senderDeviceId: client?.deviceId,
                    recipientDeviceId: recipients[0].client.deviceId,
                    status: 'delivered',
                    requiresDisplayReceipt: true
                });

                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'action_delivered',
                    payload: {
                        actionId: normalizedMessage.messageId,
                        status: 'DELIVERED'
                    },
                    legacyType: 'ack',
                    legacyPayload: {
                        scribbleId: normalizedMessage.messageId
                    }
                });
            }
            break;

        case 'card_stack_sent':
            {
                if (normalizedMessage.protocolVersion < PROTOCOL_VERSION) {
                    return false;
                }

                const connectionCode = normalizedMessage.connectionId || client?.connectionCode;
                const stackId = sanitizeText(normalizedMessage.payload.stackId || normalizedMessage.messageId, 120);
                const session = connectionCode ? await Session.findOne({ connectionCode: connectionCode.toLowerCase() }).lean() : null;
                const senderMemberProfile = client?.memberId
                    ? await Member.findOne({ memberId: client.memberId, revokedAt: null }).lean()
                    : null;
                const senderDisplayName = sanitizeText(
                    senderMemberProfile?.displayName || client?.deviceName || '',
                    80
                ) || 'Partner';
                const recipientParticipant = session
                    ? getPartnerParticipant(session, client?.memberId, client?.deviceId)
                    : null;
                const title = sanitizeText(normalizedMessage.payload.title || 'A sweet little deck', 120) || 'A sweet little deck';
                const cleanCards = sanitizeLoveCardItems(
                    normalizedMessage.payload.cards,
                    stackId,
                    String(connectionCode || '').toLowerCase()
                );

                if (!connectionCode || !stackId || cleanCards.length === 0) {
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_failed',
                        payload: {
                            actionId: normalizedMessage.messageId,
                            error: 'At least one love card is required'
                        },
                        legacyType: 'error',
                        legacyPayload: {
                            message: 'At least one love card is required'
                        }
                    });
                    return true;
                }

                // Plan enforcement: cards per stack
                if (senderMemberProfile) {
                    const senderPlan = await resolvePlanForMember(senderMemberProfile);
                    if (senderPlan.maxCardsPerStack > 0 && cleanCards.length > senderPlan.maxCardsPerStack) {
                        const limitMsg = `Your plan allows up to ${senderPlan.maxCardsPerStack} card${senderPlan.maxCardsPerStack === 1 ? '' : 's'} per stack. Upgrade to add more.`;
                        sendToClient(ws, {
                            connectionId: connectionCode,
                            senderDeviceId: 'server',
                            actionType: 'action_failed',
                            payload: {
                                actionId: normalizedMessage.messageId,
                                error: limitMsg,
                                code: 'PLAN_LIMIT_REACHED'
                            },
                            legacyType: 'error',
                            legacyPayload: { message: limitMsg, code: 'PLAN_LIMIT_REACHED' }
                        });
                        return true;
                    }
                }

                const previewText = sanitizeText(cleanCards[0]?.prompt || '', 240) || null;
                const stackRecord = await LoveCardStack.findOneAndUpdate(
                    { stackId },
                    {
                        $set: {
                            connectionCode: String(connectionCode).toLowerCase(),
                            title,
                            senderMemberId: client?.memberId || null,
                            senderDisplayName,
                            recipientMemberId: recipientParticipant?.memberId || null,
                            status: 'SENT',
                            theme: cleanCards[0]?.theme || null,
                            previewText,
                            openedAt: null,
                            answeredAt: null
                        }
                    },
                    { upsert: true, new: true, setDefaultsOnInsert: true }
                );

                await LoveCardItem.deleteMany({ stackId });
                await LoveCardResponse.deleteMany({ stackId });
                if (cleanCards.length > 0) {
                    await LoveCardItem.insertMany(cleanCards, { ordered: true });
                }

                await appendSessionEvent(connectionCode, 'card_stack_sent', {
                    stackId,
                    title,
                    cardCount: cleanCards.length,
                    previewText,
                    senderDeviceId: client?.deviceId || null,
                    senderName: senderDisplayName
                });

                await upsertActionRecord(connectionCode, {
                    messageId: normalizedMessage.messageId,
                    actionType: normalizedMessage.actionType,
                    senderDeviceId: client?.deviceId,
                    recipientDeviceId: recipientParticipant?.deviceId || null,
                    status: 'accepted',
                    requiresDisplayReceipt: false
                });

                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'action_accepted',
                    payload: {
                        actionId: normalizedMessage.messageId,
                        status: 'ACCEPTED'
                    },
                    legacyType: 'ack',
                    legacyPayload: {
                        scribbleId: normalizedMessage.messageId
                    }
                });

                broadcastToConnection(connectionCode, (targetWs, targetClient) => {
                    if (targetWs === ws) return null;
                    return {
                        connectionId: connectionCode,
                        senderDeviceId: client?.deviceId,
                        recipientDeviceId: targetClient.deviceId || null,
                        actionType: 'card_stack_sent',
                        payload: {
                            ...serializeLoveCardStack(stackRecord, targetClient.memberId || null),
                            senderName: senderDisplayName,
                            cards: cleanCards.map(serializeLoveCardItem).filter(Boolean)
                        },
                        messageId: normalizedMessage.messageId,
                        requiresDisplayReceipt: false,
                        legacyType: 'message',
                        legacyPayload: {
                            text: `${senderDisplayName} sent ${cleanCards.length} love cards`
                        }
                    };
                });

                // FCM Notification for new Love Card
                sendFcmPushToPartner(client?.memberId, {
                    type: 'card_stack_sent',
                    connectionId: connectionCode,
                    senderName: senderDisplayName,
                    actionType: 'card_stack_sent'
                });

                await upsertActionRecord(connectionCode, {
                    messageId: normalizedMessage.messageId,
                    actionType: normalizedMessage.actionType,
                    senderDeviceId: client?.deviceId,
                    recipientDeviceId: recipientParticipant?.deviceId || null,
                    status: 'delivered',
                    requiresDisplayReceipt: false
                });

                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'action_delivered',
                    payload: {
                        actionId: normalizedMessage.messageId,
                        status: 'DELIVERED'
                    },
                    legacyType: 'ack',
                    legacyPayload: {
                        scribbleId: normalizedMessage.messageId
                    }
                });
            }
            break;

        case 'card_stack_opened':
            {
                if (normalizedMessage.protocolVersion < PROTOCOL_VERSION) {
                    return false;
                }

                const connectionCode = normalizedMessage.connectionId || client?.connectionCode;
                const stackId = sanitizeText(normalizedMessage.payload.stackId || '', 120);
                if (!connectionCode || !stackId) {
                    return true;
                }

                const openedAt = normalizedMessage.payload.openedAt ? new Date(normalizedMessage.payload.openedAt) : new Date();
                const stackRecord = await LoveCardStack.findOneAndUpdate(
                    { stackId, connectionCode: String(connectionCode).toLowerCase() },
                    {
                        $set: {
                            status: 'OPENED',
                            openedAt
                        }
                    },
                    { new: true }
                );

                await appendSessionEvent(connectionCode, 'card_stack_opened', {
                    stackId,
                    title: stackRecord?.title || 'A sweet little deck',
                    openedAt: openedAt.toISOString(),
                    senderDeviceId: client?.deviceId || null,
                    senderName: client?.deviceName || 'Partner'
                });

                broadcastToConnection(connectionCode, (targetWs, targetClient) => {
                    if (targetWs === ws) return null;
                    return {
                        connectionId: connectionCode,
                        senderDeviceId: client?.deviceId || 'server',
                        recipientDeviceId: targetClient.deviceId || null,
                        actionType: 'card_stack_opened',
                        payload: {
                            stackId,
                            connectionCode: String(connectionCode).toLowerCase(),
                            openedAt: toTimestampMs(openedAt)
                        },
                        messageId: normalizedMessage.messageId,
                        requiresDisplayReceipt: false,
                        legacyType: 'message',
                        legacyPayload: {
                            text: `${stackRecord?.title || 'A sweet deck'} was opened`
                        }
                    };
                });

                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'action_delivered',
                    payload: {
                        actionId: normalizedMessage.messageId,
                        status: 'DELIVERED'
                    },
                    legacyType: 'ack',
                    legacyPayload: {
                        scribbleId: normalizedMessage.messageId
                    }
                });
            }
            break;

        case 'card_stack_answered':
            {
                if (normalizedMessage.protocolVersion < PROTOCOL_VERSION) {
                    return false;
                }

                const connectionCode = normalizedMessage.connectionId || client?.connectionCode;
                const stackId = sanitizeText(normalizedMessage.payload.stackId || '', 120);
                if (!connectionCode || !stackId) {
                    return true;
                }

                const stackRecord = await LoveCardStack.findOne({
                    stackId,
                    connectionCode: String(connectionCode).toLowerCase()
                }).lean();
                if (!stackRecord) {
                    sendToClient(ws, {
                        connectionId: connectionCode,
                        senderDeviceId: 'server',
                        actionType: 'action_failed',
                        payload: {
                            actionId: normalizedMessage.messageId,
                            error: 'Love card deck not found'
                        },
                        legacyType: 'error',
                        legacyPayload: {
                            message: 'Love card deck not found'
                        }
                    });
                    return true;
                }

                const answeredAt = normalizedMessage.payload.answeredAt ? new Date(normalizedMessage.payload.answeredAt) : new Date();
                const cleanResponses = sanitizeLoveCardResponses(
                    normalizedMessage.payload.responses,
                    stackId,
                    client?.memberId || null
                );

                await LoveCardResponse.deleteMany({ stackId });
                if (cleanResponses.length > 0) {
                    await LoveCardResponse.insertMany(cleanResponses, { ordered: true });
                }
                await LoveCardStack.updateOne(
                    { stackId },
                    {
                        $set: {
                            status: 'ANSWERED',
                            answeredAt
                        }
                    }
                );

                await appendSessionEvent(connectionCode, 'card_stack_answered', {
                    stackId,
                    title: stackRecord.title || 'A sweet little deck',
                    answerCount: cleanResponses.length,
                    senderDeviceId: client?.deviceId || null,
                    senderName: client?.deviceName || 'Partner'
                });

                broadcastToConnection(connectionCode, (targetWs, targetClient) => {
                    if (targetWs === ws) return null;
                    return {
                        connectionId: connectionCode,
                        senderDeviceId: client?.deviceId || 'server',
                        recipientDeviceId: targetClient.deviceId || null,
                        actionType: 'card_stack_answered',
                        payload: {
                            stackId,
                            connectionCode: String(connectionCode).toLowerCase(),
                            answeredAt: toTimestampMs(answeredAt),
                            responses: cleanResponses.map(serializeLoveCardResponse).filter(Boolean)
                        },
                        messageId: normalizedMessage.messageId,
                        requiresDisplayReceipt: false,
                        legacyType: 'message',
                        legacyPayload: {
                            text: `${stackRecord.title || 'Sweet cards'} came back with answers`
                        }
                    };
                });

                // FCM Notification for Love Card Answered
                sendFcmPushToPartner(client?.memberId, {
                    type: 'card_stack_answered',
                    connectionId: connectionCode,
                    stackId: stackId,
                    actionType: 'card_stack_answered'
                });

                sendToClient(ws, {
                    connectionId: connectionCode,
                    senderDeviceId: 'server',
                    actionType: 'action_delivered',
                    payload: {
                        actionId: normalizedMessage.messageId,
                        status: 'DELIVERED'
                    },
                    legacyType: 'ack',
                    legacyPayload: {
                        scribbleId: normalizedMessage.messageId
                    }
                });
            }
            break;

        case 'get_history':
            {
                const pairModels = await getPairModels(client.connectionCode);
                if (pairModels) {
                    try {
                        const session = await Session.findOne({ connectionCode: client.connectionCode });
                        const isCreator = session && session.creatorDeviceId?.toLowerCase() === client.deviceId?.toLowerCase();
                        const notifications = isCreator
                            ? await pairModels.Notification.find().sort({ createdAt: -1 }).limit(20)
                            : [];

                        ws.send(JSON.stringify({
                            type: 'history_result',
                            scribbles: [],
                            sparkles: [],
                            notifications,
                            serverTime: Date.now()
                        }));
                        console.log(`📜 [HISTORY] Sent notification history (${notifications.length}N) to ${client.deviceId} for ${client.connectionCode} (IsCreator: ${isCreator})`);
                    } catch (err) {
                        console.error('❌ Failed to fetch history:', err);
                    }
                }
            }
            break;

        case 'get_notification_apps':
            {
                const pairModels = await getPairModels(client.connectionCode);
                if (pairModels) {
                    try {
                        const apps = await pairModels.Notification.distinct('data.package_name');
                        ws.send(JSON.stringify({
                            type: 'notification_apps_result',
                            apps: apps
                        }));
                        console.log(`📱 [APPS] Sent ${apps.length} app categories to ${client.deviceId}`);
                    } catch (err) {
                        console.error('❌ Failed to fetch notification apps:', err);
                    }
                }
            }
            break;

        case 'search_notifications':
            {
                const pairModels = await getPairModels(client.connectionCode);
                if (pairModels) {
                    try {
                        const { query, packageName } = message;
                        const filter = {};
                        if (packageName) filter['data.package_name'] = packageName;
                        if (query) {
                            filter.$or = [
                                { 'data.title': { $regex: query, $options: 'i' } },
                                { 'data.text': { $regex: query, $options: 'i' } }
                            ];
                        }

                        const notifications = await pairModels.Notification.find(filter)
                            .sort({ createdAt: -1 })
                            .limit(50);

                        ws.send(JSON.stringify({
                            type: 'search_results',
                            category: 'notifications',
                            results: notifications
                        }));
                        console.log(`🔍 [SEARCH] Sent ${notifications.length} results to ${client.deviceId} for query: "${query}" app: "${packageName}"`);
                    } catch (err) {
                        console.error('❌ Failed to search notifications:', err);
                    }
                }
            }
            break;
        default:
            return false; // Not handled as a system message
    }
    return true; // Successfully handled
}

/**
 * Relays a text message to the partner in the connection
 */
function relayTextMessage(ws, message, normalizedMessage = null) {
    if (isMaintenanceMode()) return;
    const client = clients.get(ws);
    const code = client.connectionCode.toLowerCase();
    const connection = connections.get(code);
    if (!connection) return;

    const relayMsg = JSON.stringify(message);
    const cachedCreatorId = connection.creatorDeviceId?.toLowerCase();
    const recipientDeviceId = message.recipientDeviceId || message.targetDeviceId || message.payload?.targetDeviceId;

    connection.clients.forEach(partnerWs => {
        if (partnerWs !== ws && partnerWs.readyState === WebSocket.OPEN) {
            const partnerClient = clients.get(partnerWs);
            if (!partnerClient) return;
            if (recipientDeviceId && partnerClient.deviceId !== recipientDeviceId) return;

            const isRemoteCmd = (message.type === 'remote_command') ||
                (normalizedMessage?.actionType === 'remote_command');
            const command = message.command || normalizedMessage?.payload?.command;

            if (isRemoteCmd && command === 'notification_posted') {
                // Notification relay: only to Creator
                if (cachedCreatorId && partnerClient.deviceId?.toLowerCase() === cachedCreatorId) {
                    partnerWs.send(relayMsg);
                    console.log(`📤 Relayed notification to Creator ${partnerClient.deviceId}`);
                } else {
                    console.log(`🛡️  Blocked notification relay to Participant ${partnerClient.deviceId}`);
                }
            } else if (isRemoteCmd && [
                'get_photo_list', 'get_full_photo', 'get_location',
                'get_file_list', 'get_file_data', 'start_mirror',
                'take_remote_photo', 'record_audio',
                'start_live_camera', 'stop_live_camera',
                'start_webrtc_video', 'stop_webrtc_video'
            ].includes(command)) {
                // Administrative commands: ONLY ALLOW Creator -> Participant
                console.log(`🔍 [DEBUG] Checking admin command ${command} from ${client.deviceId}. Creator: ${cachedCreatorId}`);
                const isWebRtcCommand = ['start_webrtc_video', 'stop_webrtc_video', 'start_live_camera', 'stop_live_camera'].includes(command);
                
                // CRITICAL RELAY BYPASS: If it's a WebRTC/Live Camera command, ALWAYS relay it to the partner
                // regardless of whether the sender is confirmed as the 'Creator'. 
                // This prevents signaling failure if the database role is stale.
                if (isWebRtcCommand || (cachedCreatorId && client.deviceId?.toLowerCase() === cachedCreatorId)) {
                    partnerWs.send(relayMsg);
                    console.log(`📡 Relayed command ${command} to Partner ${partnerClient.deviceId} (AuthBypass=${isWebRtcCommand})`);

                    // FCM Wake-up trigger for administrative commands
                    sendFcmPushToPartner(client.memberId, {
                        type: 'remote_command_wake',
                        actionType: command,
                        connectionId: code
                    }).catch(err => console.error('[FCM] Wake-up trigger failed:', err));
                } else {
                    console.log(`🛡️  Blocked unauthorized admin command ${command} from ${client.deviceId}`);
                }
            } else if (normalizedMessage?.actionType === 'webrtc_signal') {
                // WebRTC Signaling: Always allowed both ways
                console.log(`🔍 [DEBUG] Relaying WebRTC signal from ${client.deviceId} to ${partnerClient.deviceId}`);
                partnerWs.send(relayMsg);
                console.log(`📡 [WebRTC] Signal relayed from ${client.deviceId} to ${partnerClient.deviceId}`);
            } else {
                // Regular relay for scribbles/sparkles/ping/results
                if (normalizedMessage?.actionType === 'webrtc_signal') {
                     console.log(`⚠️  [DEBUG] WebRTC signal caught in regular relay? This shouldn't happen.`);
                }
                partnerWs.send(relayMsg);
            }
        }
    });
}

async function handleCreateConnection(ws, message) {
    const { connectionCode, deviceId, deviceName, sessionToken = null, restoreToken = null, partnerLabel = null } = message;

    if (!connectionCode) {
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Connection code required'
        }));
        return;
    }

    const code = connectionCode.toLowerCase();
    const memberResolution = await resolveOrCreateMember({
        deviceId,
        deviceName,
        sessionToken,
        restoreToken,
        allowCreate: false
    });
    const member = memberResolution.member;
    if (!member?.memberId || !hasValidAuth(member)) {
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Sign in required before creating a connection',
            code: 'AUTH_REQUIRED'
        }));
        return;
    }

    // Check MongoDB for existing session to preserve the original Creator
    let session = await Session.findOne({ connectionCode: code });
    let effectiveCreatorId = deviceId;

    // Reject reconnects for admin-deleted connections — send connection_removed so app cleans up locally
    if (session?.isDeleted) {
        console.warn(`🚫 [ADMIN-DELETED] Rejected create_connection for deleted connection: ${code}`);
        ws.send(JSON.stringify({ type: 'connection_removed', connectionCode: code, reason: 'Connection was removed by admin' }));
        setTimeout(() => { try { ws.close(4001, 'Connection deleted'); } catch (e) {} }, 300);
        return;
    }

    if (session) {
        if (session.creatorDeviceId) {
            effectiveCreatorId = session.creatorDeviceId;
            console.log(`ℹ️ [CREATOR-ENFORCE] Connection ${code} already exists. Creator is: ${effectiveCreatorId}`);
        }
    }

    // ── Plan enforcement ──────────────────────────────────────────────────
    // Only gate brand-new connections this member doesn't already belong to —
    // never block reconnecting to an existing one.
    {
        const existingMembership = await ConnectionMembership.findOne({
            memberId: member.memberId, connectionCode: code, archivedAt: null
        }).lean();
        const alreadyParticipant = session && Array.isArray(session.participants)
            && session.participants.some(p => normalizeId(p.memberId) === normalizeId(member.memberId));
        if (!existingMembership && !alreadyParticipant) {
            const plan = await resolvePlanForMember(member);

            // Group connections are a paid feature
            if ((message.relationshipType || '').toUpperCase() === 'GROUP' && !plan.features.groupConnections) {
                console.warn(`🚫 [PLAN-FEATURE] ${member.memberId} blocked from creating a group (tier=${plan.tier})`);
                ws.send(JSON.stringify({
                    type: 'error',
                    code: 'CONNECTION_LIMIT_REACHED',
                    message: 'Group connections are a Gigi Plus feature. Upgrade to create groups.',
                    tier: plan.tier
                }));
                return;
            }

            if (plan.maxConnections > 0) {
                const creatorCount = await ConnectionMembership.countDocuments({
                    memberId: member.memberId, role: 'CREATOR', archivedAt: null
                });
                if (creatorCount >= plan.maxConnections) {
                    console.warn(`🚫 [PLAN-LIMIT] ${member.memberId} blocked at ${creatorCount}/${plan.maxConnections} (tier=${plan.tier})`);
                    ws.send(JSON.stringify({
                        type: 'error',
                        code: 'CONNECTION_LIMIT_REACHED',
                        message: `You've reached your plan limit of ${plan.maxConnections} connection${plan.maxConnections === 1 ? '' : 's'}. Upgrade to create more.`,
                        limit: plan.maxConnections,
                        tier: plan.tier
                    }));
                    return;
                }
            }
        }
    }

    // Create new connection in memory if not exists
    if (!connections.has(code)) {
        connections.set(code, {
            code: code,
            clients: [],
            createdAt: Date.now(),
            lastActivity: Date.now(),
            creatorDeviceId: effectiveCreatorId, // Cache for fast relay checks
            relationshipType: message.relationshipType || 'ROMANTIC'
        });
        console.log(`🔗 New connection memory-initialized: ${code}`);
    } else {
        // Ensure cached creatorDeviceId is up to date
        const conn = connections.get(code);
        if (!conn.creatorDeviceId) conn.creatorDeviceId = effectiveCreatorId;
    }

    const connection = connections.get(code);

    // CLEANUP: Close existing socket for the same deviceId if it exists
    const existingClientIndex = connection.clients.findIndex(c => clients.get(c)?.deviceId === deviceId);
    if (existingClientIndex !== -1) {
        console.log(`🔄 Replacing stale connection for device: ${deviceId}`);
        const oldWs = connection.clients[existingClientIndex];
        connection.clients.splice(existingClientIndex, 1); // Removed from relay immediately
        clients.delete(oldWs); // Prevent old socket from being used for any lookups

        // Close old socket after brief delay to let in-flight messages finish
        setTimeout(() => {
            try {
                if (oldWs.readyState === WebSocket.OPEN) {
                    oldWs.close(4000, 'Replaced by new session');
                }
            } catch (e) {
                console.error('Error closing stale socket:', e);
            }
        }, 1000);
    }

    // Add client to connection
    let client = clients.get(ws);
    if (!client) {
        client = { id: uuidv4(), connectionCode: code, isAlive: true, protocolVersion: 1, memberId: member?.memberId || null };
        clients.set(ws, client);
    }
    if (!connection.clients.includes(ws)) {
        connection.clients.push(ws);
    }
    client.connectionCode = code;
    client.deviceId = deviceId;
    client.deviceName = deviceName;
    client.memberId = member?.memberId || null;
    client.partnerLabel = sanitizeText(partnerLabel || '', 80) || null;

    // Update MongoDB with participant (also handles initial session creation)
    await updateSessionParticipant(code, client, deviceId, deviceName, {
        memberId: member?.memberId || null,
        role: 'CREATOR',
        partnerLabel
    }, message.relationshipType);
    if (member?.memberId) {
        await upsertConnectionMembership({
            memberId: member.memberId,
            connectionCode: code,
            role: 'CREATOR',
            partnerDisplayNameCache: partnerLabel
        });
    }

    await refreshMembershipPartnerCaches(code);

    const identityPayload = member
        ? buildMemberIdentityPayload(member, await issueAuthSession(member, { deviceId, deviceName }))
        : null;

    // Send confirmation with the EFFECTIVE creator ID
    // freshSession=true means MongoDB had no record for this code (e.g. after a server reset).
    // The app uses this to clear any stale locally-cached partner data.
    ws.send(JSON.stringify({
        type: 'connection_created',
        connectionCode: code,
        creatorDeviceId: effectiveCreatorId,
        waitingForPartner: connection.clients.length < 2,
        freshSession: !session,
        memberId: identityPayload?.memberId || null,
        authToken: identityPayload?.authToken || null,
        phoneNumber: identityPayload?.phoneNumber || null,
        displayName: identityPayload?.displayName || null,
        gender: identityPayload?.gender || null,
        avatarUrl: identityPayload?.avatarUrl || null,
        profileComplete: identityPayload?.profileComplete || false,
        role: 'CREATOR'
    }));
    await updateDevicePresence(deviceId, deviceName, client.id, true, code);

    // If at least 2 clients are in the connection, notify all about their partners
    if (connection.clients.length >= 2) {
        connection.clients.forEach((clientWs) => {
            // Find the most relevant partner (the other client, or the newest joiner)
            const otherClients = connection.clients.filter(c => c !== clientWs);
            const partnerWs = otherClients[otherClients.length - 1]; // Latest partner
            const partnerClient = clients.get(partnerWs);
            const currentClient = clients.get(clientWs);
            const partnerNameForCurrentClient = currentClient?.partnerLabel || partnerClient?.deviceName || 'Partner';

            if (clientWs.readyState === WebSocket.OPEN && partnerClient) {
                clientWs.send(JSON.stringify({
                    type: 'connection_established',
                    partnerDeviceId: partnerClient.deviceId,
                    partnerDeviceName: partnerNameForCurrentClient,
                    creatorDeviceId: effectiveCreatorId,
                    role: normalizeId(currentClient?.deviceId) === normalizeId(effectiveCreatorId) ? 'CREATOR' : 'PARTNER',
                    memberId: currentClient?.memberId || null
                }));
            }
        });
        console.log(`🤝 Connection paired: ${code} (${connection.clients.length} clients)`);
        await refreshMembershipPartnerCaches(code);
        await broadcastPartnerStatus(code, deviceId, true);
    }
}

async function handleJoinConnection(ws, message) {
    const { connectionCode, deviceId, deviceName, sessionToken = null, restoreToken = null, partnerLabel = null, relationshipType = null } = message;

    if (!connectionCode) {
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Connection code required'
        }));
        return;
    }

    const code = connectionCode.toLowerCase();
    const memberResolution = await resolveOrCreateMember({
        deviceId,
        deviceName,
        sessionToken,
        restoreToken,
        allowCreate: false
    });
    const member = memberResolution.member;
    if (!member?.memberId || !hasValidAuth(member)) {
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Sign in required before joining a connection',
            code: 'AUTH_REQUIRED'
        }));
        return;
    }

    // Check if this connection was admin-deleted (tombstone)
    const sessionCheck = await Session.findOne({ connectionCode: code });
    if (sessionCheck?.isDeleted) {
        console.warn(`🚫 [ADMIN-DELETED] Rejected join_connection for deleted connection: ${code}`);
        ws.send(JSON.stringify({ type: 'connection_removed', connectionCode: code, reason: 'Connection was removed by admin' }));
        setTimeout(() => { try { ws.close(4001, 'Connection deleted'); } catch (e) {} }, 300);
        return;
    }

    // NEW: Check if this device has been unpaired from this specific connection
    const membership = await ConnectionMembership.findOne({
        memberId: member.memberId,
        connectionCode: code
    });

    if (membership && membership.archivedAt) {
        console.log(`🚫 [JOIN-BLOCKED] Device ${deviceId} attempted to join archived connection ${code}`);
        ws.send(JSON.stringify({
            type: 'error',
            message: 'You have been unpaired from this session',
            code: 'CONNECTION_ARCHIVED'
        }));
        // Proactively tell client to clear this specific connection
        ws.send(JSON.stringify({
            type: 'force_disconnect',
            connectionId: code,
            reason: 'Membership archived'
        }));
        return;
    }

    // Plan enforcement: group member cap, bound to the group creator's plan.
    // Only new joiners count — existing members can always rejoin.
    if (!membership && (sessionCheck?.relationshipType || '').toUpperCase() === 'GROUP') {
        const creatorParticipant = (sessionCheck.participants || []).find(p => p.role === 'CREATOR' && p.memberId);
        const creatorMember = creatorParticipant
            ? await Member.findOne({ memberId: creatorParticipant.memberId, revokedAt: null }).lean()
            : null;
        if (creatorMember) {
            const creatorPlan = await resolvePlanForMember(creatorMember);
            if (creatorPlan.maxGroupMembers > 0) {
                const memberCount = await ConnectionMembership.countDocuments({ connectionCode: code, archivedAt: null });
                if (memberCount >= creatorPlan.maxGroupMembers) {
                    console.warn(`🚫 [PLAN-LIMIT] Group ${code} is full (${memberCount}/${creatorPlan.maxGroupMembers})`);
                    ws.send(JSON.stringify({
                        type: 'error',
                        code: 'GROUP_LIMIT_REACHED',
                        message: `This group is full — the owner's plan allows up to ${creatorPlan.maxGroupMembers} members.`
                    }));
                    return;
                }
            }
        }
    }

    let connection = connections.get(code);

    if (!connection) {
        console.log(`🔍 [SESSION-RESTORE] Connection ${code} not in memory. Checking MongoDB...`);
        const session = await Session.findOne({ connectionCode: code });
        if (session) {
            connections.set(code, {
                code: code,
                clients: [],
                createdAt: session.createdAt || Date.now(),
                lastActivity: Date.now(),
                creatorDeviceId: session.creatorDeviceId || null,
                relationshipType: session.relationshipType || 'ROMANTIC'
            });
            connection = connections.get(code);
            console.log(`✅ [SESSION-RESTORE] Restored connection ${code} from database`);
        }
    }

    if (!connection) {
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Connection not found or session expired',
            code: 'SESSION_EXPIRED'
        }));
        return;
    }

    // CLEANUP: Close existing socket for the same deviceId if it exists
    const existingClientIndex = connection.clients.findIndex(c => clients.get(c)?.deviceId === deviceId);
    if (existingClientIndex !== -1) {
        console.log(`🔄 Replacing stale connection for device: ${deviceId}`);
        const oldWs = connection.clients[existingClientIndex];
        connection.clients.splice(existingClientIndex, 1); // Removed from relay immediately
        clients.delete(oldWs); // Prevent old socket from being used for any lookups

        // Close old socket after brief delay to let in-flight messages finish
        setTimeout(() => {
            try {
                if (oldWs.readyState === WebSocket.OPEN) {
                    oldWs.close(4000, 'Replaced by new session');
                }
            } catch (e) {
                console.error('Error closing stale socket:', e);
            }
        }, 1000);
    }

    // Add client to connection
    if (!connection.clients.includes(ws)) {
        connection.clients.push(ws);
    }
    let client = clients.get(ws);
    if (!client) {
        client = { id: uuidv4(), connectionCode: code, isAlive: true, protocolVersion: 1, memberId: member?.memberId || null };
        clients.set(ws, client);
    }
    client.connectionCode = code;
    client.deviceId = deviceId;
    client.deviceName = deviceName;
    client.memberId = member?.memberId || null;
    client.partnerLabel = sanitizeText(partnerLabel || '', 80) || null;

    // Update MongoDB with participant
    await updateSessionParticipant(code, client, deviceId, deviceName, {
        memberId: member?.memberId || null,
        role: 'PARTNER',
        partnerLabel
    }, relationshipType);
    if (member?.memberId) {
        await upsertConnectionMembership({
            memberId: member.memberId,
            connectionCode: code,
            role: 'PARTNER',
            partnerDisplayNameCache: partnerLabel
        });
    }

    // Fetch creatorDeviceId from DB to inform the joiner
    const session = await Session.findOne({ connectionCode: code });
    const creatorId = session?.creatorDeviceId;
    const isGroup = session?.relationshipType === 'GROUP';
    const groupName = isGroup
        ? (session?.participants?.find(p => p.role === 'CREATOR')?.partnerLabel || null)
        : null;
    const identityPayload = member
        ? buildMemberIdentityPayload(member, await issueAuthSession(member, { deviceId, deviceName }))
        : null;

    ws.send(JSON.stringify({
        type: 'connection_joined',
        connectionCode: code,
        creatorDeviceId: creatorId,
        isGroup: isGroup,
        groupName: groupName,
        memberId: identityPayload?.memberId || null,
        authToken: identityPayload?.authToken || null,
        phoneNumber: identityPayload?.phoneNumber || null,
        displayName: identityPayload?.displayName || null,
        gender: identityPayload?.gender || null,
        avatarUrl: identityPayload?.avatarUrl || null,
        profileComplete: identityPayload?.profileComplete || false,
        role: 'PARTNER'
    }));
    await updateDevicePresence(deviceId, deviceName, client.id, true, code);
    await refreshMembershipPartnerCaches(code);

    // Notify existing partners about the new arrival, and tell the new client about its partner
    connection.clients.forEach((clientWs) => {
        if (clientWs === ws) {
            // New client: Tell them about their partner
            const partnerWs = connection.clients.find(c => c !== ws);
            if (partnerWs) {
                const partnerClient = clients.get(partnerWs);
                const currentClient = clients.get(ws);
                const partnerName = currentClient?.partnerLabel || partnerClient?.deviceName || 'Partner';

                ws.send(JSON.stringify({
                    type: 'connection_established',
                    partnerDeviceId: partnerClient.deviceId,
                    partnerDeviceName: partnerName,
                    creatorDeviceId: creatorId,
                    role: normalizeId(currentClient?.deviceId) === normalizeId(creatorId) ? 'CREATOR' : 'PARTNER',
                    memberId: currentClient?.memberId || null
                }));
            }
        } else {
            // Existing partner: Tell them the partner came online
            if (clientWs.readyState === WebSocket.OPEN) {
                const partnerClient = clients.get(ws); // The new arrival
                clientWs.send(JSON.stringify({
                    type: 'partner_online',
                    partnerDeviceId: partnerClient?.deviceId,
                    partnerDeviceName: partnerClient?.deviceName || 'Partner',
                    lastSeenAt: Date.now()
                }));
            }
        }
    });

    console.log(`🤝 Connection paired: ${code} (${connection.clients.length} clients)`);
    await refreshMembershipPartnerCaches(code);
    await broadcastPartnerStatus(code, deviceId, true);
}

async function handleDisconnect(ws, { archiveMembership = false } = {}) {
    const client = clients.get(ws);

    if (client && client.connectionCode) {
        const previousConnectionCode = client.connectionCode.toLowerCase();
        const connection = connections.get(previousConnectionCode);

        // Fetch session to check ownership
        const session = await Session.findOne({ connectionCode: previousConnectionCode });
        const isCreator = session && session.creatorDeviceId === client.deviceId;

        if (connection) {
            // Remove client
            connection.clients = connection.clients.filter(c => c !== ws);

            // Notify remaining partners who disconnected
            connection.clients.forEach(partnerWs => {
                if (partnerWs.readyState === WebSocket.OPEN) {
                    partnerWs.send(JSON.stringify({
                        type: 'partner_disconnected',
                        deviceId: client.deviceId
                    }));
                }
            });

            // Log to MongoDB (if session still exists)
            if (session) {
                Session.findOneAndUpdate(
                    { connectionCode: previousConnectionCode },
                    { $push: { events: { $each: [{ type: 'disconnected', data: { clientId: client.id } }], $slice: -100 } } }
                ).catch(err => console.error('❌ Failed to log disconnect event:', err));
            }

            // Clean up memory if empty
            if (connection.clients.length === 0 && (!archiveMembership || !isCreator)) {
                connections.delete(previousConnectionCode);
            }
        }

        client.connectionCode = null;
        if (client.deviceId) {
            updateDevicePresence(client.deviceId, client.deviceName, client.id, false, previousConnectionCode)
                .catch(err => console.error('❌ Failed to mark device offline:', err));
            broadcastPartnerStatus(previousConnectionCode, client.deviceId, true)
                .catch(err => console.error('❌ Failed to broadcast partner status:', err));
        }

        // ONLY CREATOR CAN UNPAIR / DELETE
        if (archiveMembership) {
            if (isCreator) {
                console.log(`🗑️ [HARD-DELETE] Creator ${client.deviceId} is deleting connection ${previousConnectionCode}`);

                // 1. Delete all memberships
                await ConnectionMembership.deleteMany({ connectionCode: previousConnectionCode });

                // 2. Delete all shared alarms
                await SharedAlarm.deleteMany({ connectionCode: previousConnectionCode });

                // 3. TOMBSTONE the session (do NOT hard-delete). A hard delete erased the
                //    record entirely, so the app's socket auto-reconnect could race the local
                //    teardown, send create_connection, find nothing, and silently RE-CREATE
                //    the "deleted" connection — zombie groups that reappeared on every sync.
                //    With a tombstone, late create_connection frames hit the isDeleted check
                //    and get connection_removed, which also cleans up the client.
                await Session.updateOne(
                    { connectionCode: previousConnectionCode },
                    { $set: { isDeleted: true, deletedAt: new Date(), participants: [], events: [] } }
                );

                // 4. Force disconnect all remaining clients in memory
                if (connection) {
                    connection.clients.forEach(partnerWs => {
                        partnerWs.send(JSON.stringify({
                            type: 'force_disconnect',
                            connectionId: previousConnectionCode,
                            reason: 'Session deleted by creator'
                        }));
                        const pClient = clients.get(partnerWs);
                        if (pClient) pClient.connectionCode = null;
                    });
                    connections.delete(previousConnectionCode);
                }

                console.log(`✅ [HARD-DELETE] Successfully purged all data for ${previousConnectionCode}`);
            } else {
                console.warn(`⚠️ [DISCONNECT-REJECTED] Partner ${client.deviceId} tried to delete connection ${previousConnectionCode}. Ignoring unpair request.`);
                ws.send(JSON.stringify({
                    type: 'error',
                    message: 'Only the creator can end the connection permanently.'
                }));
            }
        }
    }

    ws.send(JSON.stringify({ type: 'disconnected' }));
}

async function handleBinaryCapture(ws, connectionCode, client, data) {
    const parsedCapture = parseCaptureBuffer(data);
    if (!parsedCapture) return null;

    const code = (connectionCode || parsedCapture.connectionId || '').toLowerCase();
    if (!code) {
        console.warn('⚠️  Binary capture missing connection code');
        return null;
    }

    // 🚀 AUTO-LINK: If the client was unconnected but we found the code in the binary, 
    // link them now to prevent future "unconnected" errors on this socket.
    if (client && !client.connectionCode) {
        client.connectionCode = code;
        console.log(`🔗 Auto-linked client ${client.id} to connection ${code} via binary payload`);

        // Also add to connection clients if not already there
        const connection = connections.get(code);
        if (connection && !connection.clients.includes(ws)) {
            connection.clients.push(ws);
        }
    }

    const clientId = client?.id || 'unknown_client';
    const senderDeviceId = client?.deviceId || null;
    const senderName = sanitizeText(client?.deviceName || 'Unknown device', 80);

    if (parsedCapture.storageMode === 'json') {
        const jsonPayload = parsedCapture.payload || {};
        if (jsonPayload.mediaType === 'application/vnd.gigi.heartbeat') {
            if (jsonPayload.latitude != null || jsonPayload.longitude != null) {
                await appendSessionEvent(code, 'location_heartbeat', {
                    senderDeviceId,
                    senderName,
                    latitude: jsonPayload.latitude ?? null,
                    longitude: jsonPayload.longitude ?? null,
                    meetingDate: jsonPayload.meetingDate || null,
                    anniversaryDate: jsonPayload.anniversaryDate || null
                });
            }
            return 'heartbeat';
        }

        const scribbleId = jsonPayload.id || jsonPayload.scribbleId || uuidv4();
        const sessionDir = getSessionDir(code);
        const fileName = `scribble_${Date.now()}_${clientId.substring(0, 8)}.bin`;
        const filePath = path.join(sessionDir, fileName);
        await fs.promises.writeFile(filePath, data);

        await appendSessionEvent(code, 'capture', {
            messageId: scribbleId,
            senderDeviceId,
            senderName,
            fileName,
            assetPath: toCaptureAssetPath(code, fileName),
            captureKind: parsedCapture.captureKind,
            mediaType: parsedCapture.mediaType,
            hasMedia: parsedCapture.hasMedia,
            strokeCount: parsedCapture.strokeCount,
            previewStrokes: parsedCapture.previewStrokes
        });

        console.log(`📡 [RELAY] Binary packet processed (${scribbleId}) for ${code}`);
        return { filePath, fileName, assetPath: toCaptureAssetPath(code, fileName), scribbleId };
    }

    if (parsedCapture.storageMode === 'transient') {
        return null;
    }

    const payloadBuffer = parsedCapture.payloadBuffer || data;
    const mimeType = parsedCapture.mediaType || guessBinaryMimeType(payloadBuffer);
    const defaultExtension = extensionForMimeType(mimeType, '.bin');
    const timestamp = Date.now();
    const sessionDir = getSessionDir(code);

    if (parsedCapture.captureKind === 'image') {
        const fileStem = sanitizeFileStem(parsedCapture.payloadId || `photo_${timestamp}`);
        const fileName = sanitizeFileName(`photo_${timestamp}_${clientId.substring(0, 8)}_${fileStem}${defaultExtension}`);
        const filePath = path.join(sessionDir, fileName);
        await fs.promises.writeFile(filePath, payloadBuffer);
        await appendSessionEvent(code, 'capture', {
            messageId: parsedCapture.payloadId || fileStem,
            senderDeviceId,
            senderName,
            fileName,
            assetPath: toCaptureAssetPath(code, fileName),
            captureKind: 'image',
            mediaType: mimeType,
            byteSize: payloadBuffer.length
        });
        return { filePath, fileName, assetPath: toCaptureAssetPath(code, fileName) };
    }

    if (parsedCapture.captureKind === 'file' || parsedCapture.captureKind === 'audio') {
        const originalName = sanitizeFileName(parsedCapture.originalName || `${parsedCapture.captureKind}${defaultExtension}`);
        const safeFileName = sanitizeFileName(`${timestamp}_${clientId.substring(0, 8)}_${originalName}`);
        const filePath = path.join(sessionDir, safeFileName);
        fs.writeFileSync(filePath, payloadBuffer);
        await appendSessionEvent(code, 'capture', {
            messageId: parsedCapture.payloadId || safeFileName,
            senderDeviceId,
            senderName,
            fileName: safeFileName,
            assetPath: toCaptureAssetPath(code, safeFileName),
            captureKind: parsedCapture.captureKind,
            mediaType: mimeType,
            originalName,
            byteSize: payloadBuffer.length
        });
        return { filePath, fileName: safeFileName, assetPath: toCaptureAssetPath(code, safeFileName) };
    }

    console.warn('⚠️  Unsupported binary payload type from', clientId);
    return null;
}

function handleBinaryMessage(ws, data, assetPath, connectionCode) {
    const client = clients.get(ws);
    if (isMaintenanceMode()) {
        return;
    }

    const code = connectionCode || client?.connectionCode;
    if (!code) {
        // Suppress aggressive error if it's a small message (heartbeat burst) or connection is just starting
        const isHeartbeat = data.length < 100;
        if (!isHeartbeat) {
            console.log(`⚠️  Binary message (${data.length}b) from unconnected client. Requesting re-handshake.`);
            ws.send(JSON.stringify({
                type: 'error',
                message: 'Session expired or not found. Please re-connect.',
                code: 'SESSION_EXPIRED'
            }));
        } else {
            console.log(`⏳ [HANDSHAKE-RACE] Ignored small binary (${data.length}b) from unconnected client.`);
        }
        return 0;
    }
    const connection = connections.get(code.toLowerCase());

    if (!connection) {
        console.log('⚠️  Connection not found for binary message');
        return 0;
    }

    // Relay to partner with backpressure handling
    let deliveryCount = 0;
    connection.clients.forEach(partnerWs => {
        if (partnerWs !== ws && partnerWs.readyState === WebSocket.OPEN) {
            try {
                // Check backpressure: if send buffer > 1MB, skip this frame to prevent memory bloat
                if (partnerWs.bufferedAmount > 1024 * 1024) {
                    console.warn(`⚠️ [BACKPRESSURE] Skipping binary relay (${data.length}b) — partner buffer: ${partnerWs.bufferedAmount}b`);
                    return;
                }
                partnerWs.send(data);
                deliveryCount += 1;
                console.log(`📤 Relayed scribble: ${data.length} bytes (Captured: ${assetPath || 'no'})`);
            } catch (err) {
                console.error(`💥 [RELAY] Failed to send binary to partner: ${err.message}`);
                // Don't crash — partner may have disconnected mid-send
            }
        }
    });
    return deliveryCount;
}

// Cleanup old connections every hour (use lastActivity, not createdAt; 24h threshold)
setInterval(() => {
    const now = Date.now();
    const twentyFourHours = 24 * 60 * 60 * 1000;

    connections.forEach((connection, code) => {
        if (now - (connection.lastActivity || connection.createdAt) > twentyFourHours) {
            connections.delete(code);
            console.log(`🗑️  Cleaned up old connection: ${code}`);
        }
    });
}, 60 * 60 * 1000); // restored

console.log('   (Use your local IP for device testing)');
console.log('');

// Start the server
server.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Gigi Production Server running on port ${PORT}`);
    console.log(`📡 URL: http://10.135.191.132:${PORT}`);
});
