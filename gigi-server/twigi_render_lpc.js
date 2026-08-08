/**
 * Twigi LPC render engine v11 — full library, body types, all animations, styles.
 *
 * tools/lpc_pack.py exports per item, per side (fg/bg), per body (m/f/u):
 *   { s: "<static>.png", a: { <anim>: ["<strip>.png", nFrames] } }
 * The head is ALWAYS composited with the body (never optional, no head category).
 *
 *   - renderTwigi(config, size)     -> static PNG
 *   - renderTwigiGif(config, size)  -> animated GIF of config.anim
 *   - isAnimated(config)            -> whether a save should be GIF or PNG
 *
 * Styles: 'pixel' (crisp) | 'smooth' (xBR + HD-2D pass: sharpen/pop/toplight/rim/
 * bloom/volume; contact shadow on PNG only — GIF alpha is 1-bit).
 * NOTE: LPC art = CC-BY-SA/GPL/OGA-BY/CC0 — attribution served at /twigi/credits.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { PNG } = require('pngjs');
const { GIFEncoder, quantize, applyPalette } = require('gifenc');
const { xbr4x } = require('./xbr.js');

const DIR = path.join(__dirname, 'twigi_assets', 'lpc');
const M = JSON.parse(fs.readFileSync(path.join(DIR, 'manifest.json'), 'utf8'));
const FS_ = M.frame_size;
const Z_BODY = 10, Z_HEAD = 100;

const NICE_ANIMS = ['walk', 'alive', 'idle', 'run', 'jump', 'sit', 'spellcast',
    'slash', 'thrust', 'shoot', 'climb', 'hurt', 'emote', 'static'];
const ANIM_IDS = NICE_ANIMS.filter(a => a === 'alive' || a === 'static' || (M.anims || []).includes(a));

const DEPTH = { body: 0, head: 0.6, brows: 0.68, nose: 0.66, ears: 0.6, eyes: 0.65,
    beards: 0.7, facial: 0.7, hair: 1.0, hat: 1.2, top: 0.35, dress: 0.35,
    bottom: 0.25, shoes: 0.2, neck: 0.4, cape: 0.45, arms: 0.3, shoulders: 0.45,
    weapon: 1.1, tool: 1.1, shield: 1.15, backpack: -0.4, quiver: -0.4 };

const OPTIONAL_OFF = new Set(['eyes', 'brows', 'nose', 'ears', 'beards', 'facial',
    'dress', 'hat', 'cape', 'neck', 'arms', 'shoulders', 'weapon', 'tool',
    'shield', 'backpack', 'quiver']);
const ORDER = ['hair', 'eyes', 'brows', 'nose', 'ears', 'beards', 'facial', 'top',
    'dress', 'bottom', 'shoes', 'hat', 'neck', 'cape', 'shoulders', 'arms',
    'backpack', 'quiver', 'shield', 'weapon', 'tool'];
const LABELS = {
    gallery: 'Sets', body: 'Body', anim: 'Motion', style: 'Style', skinColor: 'Skin',
    hairColor: 'Hair color', hair: 'Hair', eyes: 'Eyes', brows: 'Brows', nose: 'Nose',
    ears: 'Ears', beards: 'Beard', facial: 'Facial', top: 'Top', dress: 'Dress',
    bottom: 'Bottom', shoes: 'Shoes', hat: 'Hat', cape: 'Cape', neck: 'Neck',
    arms: 'Arms', shoulders: 'Shoulders', weapon: 'Weapon', tool: 'Tool',
    shield: 'Shield', backpack: 'Bag', quiver: 'Quiver',
};
const STYLES = ['pixel', 'smooth'];

const cache = {};
function frame(file) {
    if (!file) return null;
    if (!(file in cache)) {
        const p = path.join(DIR, 'frames', file);
        cache[file] = fs.existsSync(p) ? PNG.sync.read(fs.readFileSync(p)) : null;
    }
    return cache[file];
}

// optional "Sets" gallery (ready-made avatars) — absent pack is fine
let GALLERY = { items: {} };
try {
    GALLERY = JSON.parse(fs.readFileSync(path.join(DIR, '..', 'gallery', 'manifest.json'), 'utf8'));
} catch (_) { }
const galleryIds = Object.keys(GALLERY.items);
const galleryCache = {};
function galleryPng(file) {
    if (!(file in galleryCache)) {
        const p = path.join(DIR, '..', 'gallery', file);
        galleryCache[file] = fs.existsSync(p) ? PNG.sync.read(fs.readFileSync(p)) : null;
    }
    return galleryCache[file];
}

const cats = M.categories || {};
const parts = { body: [], anim: ANIM_IDS.slice(), style: STYLES.slice() };
if (M.bodies.m) parts.body.push('male');
if (M.bodies.f) parts.body.push('female');
if (galleryIds.length) parts.gallery = ['none', ...galleryIds];
for (const c of ORDER) {
    if (!cats[c]) continue;
    const ids = Object.keys(cats[c].items);
    parts[c] = OPTIONAL_OFF.has(c) ? ['none', ...ids] : ids;
}
const skinHexes = M.skinColors.map(c => c.hex);
const hairHexes = M.hairColors.map(c => c.hex);
const hexToSkin = Object.fromEntries(M.skinColors.map(c => [c.hex.toLowerCase(), c.name]));
const hexToHair = Object.fromEntries(M.hairColors.map(c => [c.hex.toLowerCase(), c.name]));

function prefer(cat, ...names) {
    const ids = parts[cat] || [];
    for (const n of names) { const hit = ids.find(x => x.includes(n)); if (hit) return hit; }
    return ids.find(x => x !== 'none') || ids[0];
}

const CATALOG = {
    v: 11, style: 'gigi-lpc', parts,
    colors: { skinColor: skinHexes, hairColor: hairHexes },
    labels: LABELS,
    order: [...(parts.gallery ? ['gallery'] : []),
            'body', 'anim', 'style', 'skinColor', 'hairColor', ...ORDER.filter(c => parts[c])],
    default: {
        ...(parts.gallery ? { gallery: 'none' } : {}),
        body: parts.body[0] || 'male', anim: 'walk', style: 'pixel',
        skinColor: skinHexes[0], hairColor: hairHexes[0],
        ...Object.fromEntries(ORDER.filter(c => parts[c]).map(c =>
            [c, OPTIONAL_OFF.has(c) ? 'none' : parts[c][0]])),
        hair: prefer('hair', 'plain', 'natural', 'afro'),
        top: prefer('top', 'longsleeve_longsleeve', 'longsleeve', 'tshirt', 'shortsleeve'),
        bottom: prefer('bottom', 'pants'),
        shoes: prefer('shoes', 'shoes_basic', 'basic'),
    },
};

function rampRGB(hex) { const n = parseInt(hex.slice(1), 16); return [n >> 16, (n >> 8) & 255, n & 255]; }
function buildMap(pal, base, target) {
    const p = M.palettes[pal]; if (!p) return null;
    const s = p[base], t = p[target]; if (!s || !t) return null;
    const map = {}; const n = Math.min(s.length, t.length);
    for (let i = 0; i < n; i++) map[s[i].toLowerCase()] = rampRGB(t[i]);
    return map;
}

function recolorPx(r, g, b, recolor) {
    const key = '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
    let t = recolor[key];
    if (!t) for (const k in recolor) {
        if (Math.abs(parseInt(k.slice(1, 3), 16) - r) <= 2 &&
            Math.abs(parseInt(k.slice(3, 5), 16) - g) <= 2 &&
            Math.abs(parseInt(k.slice(5, 7), 16) - b) <= 2) { t = recolor[k]; break; }
    }
    return t;
}

function over(acc, png, col, recolor) {
    if (!png) return;
    const sx0 = col * FS_, W = png.width, d = png.data;
    for (let y = 0; y < FS_; y++) {
        for (let x = 0; x < FS_; x++) {
            const sIdx = (y * W + (sx0 + x)) * 4;
            const a = d[sIdx + 3]; if (a === 0) continue;
            let r = d[sIdx], g = d[sIdx + 1], b = d[sIdx + 2];
            if (recolor) { const t = recolorPx(r, g, b, recolor); if (t) { r = t[0]; g = t[1]; b = t[2]; } }
            const di = (y * FS_ + x) * 4, al = a / 255, ia = (acc[di + 3] / 255) * (1 - al), oa = al + ia;
            acc[di] = (r * al + acc[di] * ia) / (oa || 1);
            acc[di + 1] = (g * al + acc[di + 1] * ia) / (oa || 1);
            acc[di + 2] = (b * al + acc[di + 2] * ia) / (oa || 1);
            acc[di + 3] = oa * 255;
        }
    }
}

function normalize(config = {}) {
    const c = config || {};
    const out = {
        gallery: (parts.gallery && parts.gallery.includes(c.gallery)) ? c.gallery : 'none',
        body: parts.body.includes(c.body) ? c.body : CATALOG.default.body,
        anim: ANIM_IDS.includes(c.anim) ? c.anim : 'walk',
        style: STYLES.includes(c.style) ? c.style : 'pixel',
        skin: (c.skinColor || '').toLowerCase(),
        hairColor: (c.hairColor || '').toLowerCase(),
    };
    for (const cat of ORDER) if (parts[cat]) out[cat] = parts[cat].includes(c[cat]) ? c[cat] : CATALOG.default[cat];
    return out;
}

function variantOf(item, side, bt) {
    return item[`${side}_${bt}`] || item[`${side}_${bt === 'm' ? 'f' : 'm'}`] || item[`${side}_u`] || null;
}

function buildLayers(c) {
    const bt = c.body === 'female' ? 'f' : 'm';
    const skinMap = buildMap('body', M.recolor_base.skin, hexToSkin[c.skin] || M.recolor_base.skin);
    const hairMap = buildMap('hair', M.recolor_base.hair, hexToHair[c.hairColor] || M.recolor_base.hair);
    const L = [];
    const add = (z, v, rc, cat, back) => { if (v) L.push({ z, v, rc, cat, back: !!back }); };
    add(Z_BODY, M.bodies[bt] || M.bodies.m, skinMap, 'body');
    add(Z_HEAD, M.heads[bt] || M.heads.m, skinMap, 'head');   // head is part of the body
    for (const cat of ORDER) {
        const id = c[cat]; if (!id || id === 'none' || !cats[cat] || !cats[cat].items[id]) continue;
        const item = cats[cat].items[id];
        const rc = cats[cat].recolor === 'hair' ? hairMap : (cats[cat].recolor === 'skin' ? skinMap : null);
        add(cats[cat].zback ?? 8, variantOf(item, 'bg', bt), rc, cat, true);
        add(cats[cat].z, variantOf(item, 'fg', bt), rc, cat, false);
    }
    L.sort((a, b) => a.z - b.z);
    return L;
}

function compositeFrame(layers, anim, i) {
    const acc = new Float32Array(FS_ * FS_ * 4);
    for (const l of layers) {
        const strip = anim ? l.v.a?.[anim] : null;
        if (strip && i != null) over(acc, frame(strip[0]), i % strip[1], l.rc);
        else over(acc, frame(l.v.s), 0, l.rc);
    }
    return acc;
}

function upscale(acc, size) {
    const out = new Uint8Array(size * size * 4);
    for (let y = 0; y < size; y++) {
        const sy = Math.floor(y * FS_ / size);
        for (let x = 0; x < size; x++) {
            const sx = Math.floor(x * FS_ / size);
            const si = (sy * FS_ + sx) * 4, di = (y * size + x) * 4;
            out[di] = Math.round(acc[si]); out[di + 1] = Math.round(acc[si + 1]);
            out[di + 2] = Math.round(acc[si + 2]); out[di + 3] = Math.round(acc[si + 3]);
        }
    }
    return out;
}

function bilinearResize(src, sw, size) {
    const out = new Uint8Array(size * size * 4);
    const k = sw / size;
    for (let y = 0; y < size; y++) {
        const fy = (y + 0.5) * k - 0.5, y0 = Math.max(0, Math.floor(fy));
        const y1 = Math.min(sw - 1, y0 + 1), wy = fy - y0;
        for (let x = 0; x < size; x++) {
            const fx = (x + 0.5) * k - 0.5, x0 = Math.max(0, Math.floor(fx));
            const x1 = Math.min(sw - 1, x0 + 1), wx = fx - x0;
            const idx = [(y0 * sw + x0) * 4, (y0 * sw + x1) * 4, (y1 * sw + x0) * 4, (y1 * sw + x1) * 4];
            const w = [(1 - wx) * (1 - wy), wx * (1 - wy), (1 - wx) * wy, wx * wy];
            let r = 0, g = 0, b = 0, a = 0;
            for (let s = 0; s < 4; s++) {
                const al = src[idx[s] + 3] / 255;
                r += src[idx[s]] * al * w[s]; g += src[idx[s] + 1] * al * w[s];
                b += src[idx[s] + 2] * al * w[s]; a += al * w[s];
            }
            const di = (y * size + x) * 4;
            out[di] = a > 0 ? Math.round(r / a) : 0;
            out[di + 1] = a > 0 ? Math.round(g / a) : 0;
            out[di + 2] = a > 0 ? Math.round(b / a) : 0;
            out[di + 3] = Math.round(a * 255);
        }
    }
    return out;
}

function upscaleSmooth(acc, size) {
    const bytes = new Uint8Array(FS_ * FS_ * 4);
    for (let i = 0; i < bytes.length; i++) bytes[i] = Math.round(acc[i]);
    const up = xbr4x(new Uint32Array(bytes.buffer), FS_, FS_, { blendColors: true, scaleAlpha: true });
    const upBytes = new Uint8Array(up.buffer);
    if (size === FS_ * 4) return upBytes;
    return bilinearResize(upBytes, FS_ * 4, size);
}
function upscaleFor(style) { return style === 'smooth' ? upscaleSmooth : upscale; }

// separable box blur ×3 ≈ gaussian on a Float32 plane
function blurPlane(src, w, h, r) {
    if (r < 1) return src.slice();
    const rad = Math.max(1, Math.round(r));
    let a = src.slice(), b = new Float32Array(w * h);
    for (let pass = 0; pass < 3; pass++) {
        for (let y = 0; y < h; y++) {
            let acc = 0; const row = y * w;
            for (let x = -rad; x <= rad; x++) acc += a[row + Math.min(w - 1, Math.max(0, x))];
            for (let x = 0; x < w; x++) {
                b[row + x] = acc / (2 * rad + 1);
                acc += a[row + Math.min(w - 1, x + rad + 1)] - a[row + Math.max(0, x - rad)];
            }
        }
        for (let x = 0; x < w; x++) {
            let acc = 0;
            for (let y = -rad; y <= rad; y++) acc += b[Math.min(h - 1, Math.max(0, y)) * w + x];
            for (let y = 0; y < h; y++) {
                a[y * w + x] = acc / (2 * rad + 1);
                acc += b[Math.min(h - 1, y + rad + 1) * w + x] - b[Math.max(0, y - rad) * w + x];
            }
        }
    }
    return a;
}

// HD-2D "F" pass: unsharp -> sat/contrast -> toplight -> rim -> bloom -> volume -> shadow
function postSmoothF(rgba, size, withShadow) {
    const N = size * size, k = size / 320;
    const R = new Float32Array(N), G = new Float32Array(N), B = new Float32Array(N), A = new Float32Array(N);
    for (let i = 0; i < N; i++) { R[i] = rgba[i * 4]; G[i] = rgba[i * 4 + 1]; B[i] = rgba[i * 4 + 2]; A[i] = rgba[i * 4 + 3]; }
    const r2 = Math.max(1, Math.round(2 * k));
    const Rb = blurPlane(R, size, size, r2), Gb = blurPlane(G, size, size, r2), Bb = blurPlane(B, size, size, r2);
    for (let i = 0; i < N; i++) {
        R[i] += (R[i] - Rb[i]) * 1.2; G[i] += (G[i] - Gb[i]) * 1.2; B[i] += (B[i] - Bb[i]) * 1.2;
    }
    for (let i = 0; i < N; i++) {
        const y = Math.floor(i / size), grad = 1.08 - (y / size) * 0.16;
        const gray = 0.299 * R[i] + 0.587 * G[i] + 0.114 * B[i];
        R[i] = ((gray + (R[i] - gray) * 1.15 - 128) * 1.08 + 128) * grad;
        G[i] = ((gray + (G[i] - gray) * 1.15 - 128) * 1.08 + 128) * grad;
        B[i] = ((gray + (B[i] - gray) * 1.15 - 128) * 1.08 + 128) * grad;
    }
    const shift = Math.max(2, Math.round(4 * k));
    const rim = new Float32Array(N);
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
        const i = y * size + x;
        if (A[i] > 40 && (y < shift || A[(y - shift) * size + x] <= 40)) rim[i] = 255;
    }
    const rimB = blurPlane(rim, size, size, Math.max(1, Math.round(1.2 * k)));
    for (let i = 0; i < N; i++) {
        const a = (rimB[i] / 255) * 0.75;
        if (a > 0) { R[i] = R[i] * (1 - a) + 255 * a; G[i] = G[i] * (1 - a) + 240 * a; B[i] = B[i] * (1 - a) + 210 * a; }
    }
    const bloom = new Float32Array(N);
    for (let i = 0; i < N; i++) if (A[i] > 40 && (R[i] + G[i] + B[i]) / 3 > 205) bloom[i] = 255;
    const bloomB = blurPlane(bloom, size, size, Math.max(2, Math.round(7 * k)));
    for (let i = 0; i < N; i++) {
        const g = (bloomB[i] / 255) * 0.5;
        if (g > 0) { R[i] += g * (255 - R[i]) * 0.9; G[i] += g * (255 - G[i]) * 0.9; B[i] += g * (255 - B[i]) * 0.9; }
    }
    // baked normal-map volume (soft — 0.32/0.16/5.5 after on-device tuning)
    {
        const mask = new Uint8Array(N), lum = new Float32Array(N);
        for (let i = 0; i < N; i++) { mask[i] = A[i] > 38 ? 1 : 0; lum[i] = (R[i] + G[i] + B[i]) / 765; }
        const INF = 1e9, dist = new Float32Array(N);
        for (let i = 0; i < N; i++) dist[i] = mask[i] ? INF : 0;
        for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
            const i = y * size + x; if (dist[i] === 0) continue;
            let m = dist[i];
            if (x > 0) m = Math.min(m, dist[i - 1] + 3);
            if (y > 0) {
                m = Math.min(m, dist[i - size] + 3);
                if (x > 0) m = Math.min(m, dist[i - size - 1] + 4);
                if (x < size - 1) m = Math.min(m, dist[i - size + 1] + 4);
            }
            dist[i] = m;
        }
        let dmax = 1;
        for (let y = size - 1; y >= 0; y--) for (let x = size - 1; x >= 0; x--) {
            const i = y * size + x; if (dist[i] === 0) continue;
            let m = dist[i];
            if (x < size - 1) m = Math.min(m, dist[i + 1] + 3);
            if (y < size - 1) {
                m = Math.min(m, dist[i + size] + 3);
                if (x < size - 1) m = Math.min(m, dist[i + size + 1] + 4);
                if (x > 0) m = Math.min(m, dist[i + size - 1] + 4);
            }
            dist[i] = m;
            if (m < INF && m > dmax) dmax = m;
        }
        for (let i = 0; i < N; i++) if (dist[i] >= INF) dist[i] = dmax;
        const distB = blurPlane(dist, size, size, Math.max(2, Math.round(6 * k)));
        const lumB = blurPlane(lum, size, size, Math.max(1, Math.round(2 * k)));
        const H = new Float32Array(N);
        for (let i = 0; i < N; i++) H[i] = (distB[i] / dmax) * 0.8 + lumB[i] * 0.35;
        const s8 = 5.5, Lx = -0.45, Ly = -0.65, Lz = 0.62, Ln = Math.hypot(Lx, Ly, Lz);
        const strength = 0.32, specAmt = 0.16;
        for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
            const i = y * size + x; if (A[i] <= 0) continue;
            const gx = (H[y * size + Math.min(size - 1, x + 1)] - H[y * size + Math.max(0, x - 1)]) / 2;
            const gy = (H[Math.min(size - 1, y + 1) * size + x] - H[Math.max(0, y - 1) * size + x]) / 2;
            const nx = -gx * s8, ny = -gy * s8, nn = Math.hypot(nx, ny, 1);
            let diff = (nx * Lx + ny * Ly + Lz) / (nn * Ln); diff = diff < 0 ? 0 : diff;
            const shade = (1 - strength) + strength * diff * 1.35;
            const spec = Math.pow(diff, 10) * specAmt * 255;
            R[i] = R[i] * shade + spec; G[i] = G[i] * shade + spec; B[i] = B[i] * shade + spec;
        }
    }
    let shadowB = null;
    if (withShadow) {
        let y1 = -1, x0 = size, x1 = -1;
        for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
            if (A[y * size + x] > 40) { if (y > y1) y1 = y; if (x < x0) x0 = x; if (x > x1) x1 = x; }
        }
        if (y1 > 0 && x1 > x0) {
            const cx = (x0 + x1) / 2, wdt = (x1 - x0) * 0.42, hgt = 9 * k, cy = y1 + 1 * k;
            const sh = new Float32Array(N);
            for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
                const dx = (x - cx) / wdt, dy = (y - cy) / hgt;
                if (dx * dx + dy * dy <= 1) sh[y * size + x] = 90;
            }
            shadowB = blurPlane(sh, size, size, Math.max(2, Math.round(5 * k)));
        }
    }
    const out = new Uint8Array(N * 4);
    for (let i = 0; i < N; i++) {
        const ca = A[i] / 255;
        const sa = shadowB ? (shadowB[i] / 255) * (1 - ca) : 0;
        const oa = ca + sa, o = i * 4;
        if (oa > 0) {
            out[o] = Math.round(Math.max(0, Math.min(255, (R[i] * ca + 30 * sa) / oa)));
            out[o + 1] = Math.round(Math.max(0, Math.min(255, (G[i] * ca + 20 * sa) / oa)));
            out[o + 2] = Math.round(Math.max(0, Math.min(255, (B[i] * ca + 60 * sa) / oa)));
            out[o + 3] = Math.round(Math.min(255, oa * 255));
        }
    }
    return out;
}

// ---- gallery ("Sets") rendering ----
function galleryFrameBuf(png, sx0, sw, sh, size, nearest) {
    const out = new Uint8Array(size * size * 4);
    const scale = Math.min(size / sw, size / sh);
    const dw = Math.max(1, Math.round(sw * scale)), dh = Math.max(1, Math.round(sh * scale));
    const ox = (size - dw) >> 1, oy = (size - dh) >> 1;
    const W = png.width, d = png.data;
    for (let y = 0; y < dh; y++) for (let x = 0; x < dw; x++) {
        const o = ((oy + y) * size + (ox + x)) * 4;
        const sx = sx0 + Math.min(sw - 1, Math.floor(x / scale));
        const sy = Math.min(sh - 1, Math.floor(y / scale));
        const s = (sy * W + sx) * 4;
        out[o] = d[s]; out[o + 1] = d[s + 1]; out[o + 2] = d[s + 2]; out[o + 3] = d[s + 3];
    }
    return out;
}
function galleryItem(c) { return (c.gallery && c.gallery !== 'none') ? GALLERY.items[c.gallery] : null; }
function renderGalleryPng(g, size) {
    const png = galleryPng(g.file); if (!png) return null;
    const strip = g.kind === 'strip';
    const buf = galleryFrameBuf(png, 0, strip ? g.fw : png.width, strip ? g.fh : png.height, size, strip);
    const out = new PNG({ width: size, height: size }); out.data.set(buf);
    return PNG.sync.write(out);
}
function renderGalleryGif(g, size) {
    const png = galleryPng(g.file); if (!png) return null;
    const strip = g.kind === 'strip', frames = strip ? g.frames : 1;
    const ms = Math.round(1000 / (g.fps || 6));
    const gif = GIFEncoder();
    for (let f = 0; f < frames; f++) {
        const rgba = galleryFrameBuf(png, strip ? f * g.fw : 0,
            strip ? g.fw : png.width, strip ? g.fh : png.height, size, strip);
        for (let p = 3; p < rgba.length; p += 4) rgba[p] = rgba[p] < 128 ? 0 : 255;
        const palette = quantize(rgba, 256, { format: 'rgba4444', oneBitAlpha: true });
        gif.writeFrame(applyPalette(rgba, palette, 'rgba4444'), size, size,
            { palette, delay: ms, transparent: true, dispose: 2 });
    }
    gif.finish();
    return Buffer.from(gif.bytes());
}

function isAnimated(config) {
    const c = normalize(config);
    const g = galleryItem(c);
    if (g) return g.kind === 'strip';
    return c.anim !== 'static';
}

async function renderTwigi(config, size = 512) {
    const c = normalize(config);
    const g = galleryItem(c);
    if (g) { const out = renderGalleryPng(g, size); if (out) return out; }
    const layers = buildLayers(c);
    let buf = upscaleFor(c.style)(compositeFrame(layers, null, null), size);
    if (c.style === 'smooth') buf = postSmoothF(buf, size, true);
    const png = new PNG({ width: size, height: size });
    png.data.set(buf);
    return PNG.sync.write(png);
}

// ---- "alive" motion: breathing + depth-parallax sway, per-layer 256px composite ----
const ALIVE_S = 256, ALIVE_T = 24, ALIVE_SWAY = 1.0, ALIVE_BOUNCE = 0.5, ALIVE_MS = 85;

function extract64(file, col, recolor) {
    const png = frame(file); if (!png) return null;
    const out = new Uint8Array(FS_ * FS_ * 4);
    const sx0 = col * FS_, W = png.width, d = png.data;
    for (let y = 0; y < FS_; y++) for (let x = 0; x < FS_; x++) {
        const s = (y * W + sx0 + x) * 4, o = (y * FS_ + x) * 4;
        let r = d[s], g = d[s + 1], b = d[s + 2];
        const a = d[s + 3];
        if (a && recolor) { const t = recolorPx(r, g, b, recolor); if (t) { r = t[0]; g = t[1]; b = t[2]; } }
        out[o] = r; out[o + 1] = g; out[o + 2] = b; out[o + 3] = a;
    }
    return out;
}
function upLayer4x(buf64, style) {
    if (style === 'smooth') {
        const up = xbr4x(new Uint32Array(buf64.buffer), FS_, FS_, { blendColors: true, scaleAlpha: true });
        return new Uint8Array(up.buffer);
    }
    const S = FS_ * 4, out = new Uint8Array(S * S * 4);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
        const s = ((y >> 2) * FS_ + (x >> 2)) * 4, o = (y * S + x) * 4;
        out[o] = buf64[s]; out[o + 1] = buf64[s + 1]; out[o + 2] = buf64[s + 2]; out[o + 3] = buf64[s + 3];
    }
    return out;
}
function overOffset(acc, img, S, dx, dy) {
    for (let y = 0; y < S; y++) {
        const sy = y - dy; if (sy < 0 || sy >= S) continue;
        for (let x = 0; x < S; x++) {
            const sx = x - dx; if (sx < 0 || sx >= S) continue;
            const s = (sy * S + sx) * 4, a = img[s + 3];
            if (a === 0) continue;
            const di = (y * S + x) * 4, al = a / 255, ia = (acc[di + 3] / 255) * (1 - al), oa = al + ia;
            acc[di] = (img[s] * al + acc[di] * ia) / (oa || 1);
            acc[di + 1] = (img[s + 1] * al + acc[di + 1] * ia) / (oa || 1);
            acc[di + 2] = (img[s + 2] * al + acc[di + 2] * ia) / (oa || 1);
            acc[di + 3] = oa * 255;
        }
    }
}
async function renderAliveGif(c) {
    const S = ALIVE_S;
    const layers = buildLayers(c);
    const prepared = [];
    for (const l of layers) {
        const strip = l.v.a?.idle;
        const cols = strip ? Math.max(1, strip[1]) : 1;
        const imgs = [];
        for (let f = 0; f < 2; f++) {
            const buf = extract64(strip ? strip[0] : l.v.s, strip ? (f % cols) : 0, l.rc);
            if (buf) imgs.push(upLayer4x(buf, c.style));
        }
        if (imgs.length) prepared.push({ imgs, depth: l.back ? -0.5 : (DEPTH[l.cat] ?? 0.3) });
    }
    const gif = GIFEncoder();
    for (let t = 0; t < ALIVE_T; t++) {
        const ph = 2 * Math.PI * t / ALIVE_T;
        const sway = Math.sin(ph), bounce = Math.round(ALIVE_BOUNCE * Math.sin(2 * ph));
        const breath = t < ALIVE_T / 2 ? 0 : 1;
        const acc = new Float32Array(S * S * 4);
        for (const p of prepared) {
            overOffset(acc, p.imgs[Math.min(breath, p.imgs.length - 1)], S,
                Math.round(p.depth * ALIVE_SWAY * sway), bounce);
        }
        let rgba = new Uint8Array(S * S * 4);
        for (let i = 0; i < rgba.length; i++) rgba[i] = Math.round(acc[i]);
        if (c.style === 'smooth') rgba = postSmoothF(rgba, S, false);
        for (let p = 3; p < rgba.length; p += 4) rgba[p] = rgba[p] < 128 ? 0 : 255;
        const palette = quantize(rgba, 256, { format: 'rgba4444', oneBitAlpha: true });
        gif.writeFrame(applyPalette(rgba, palette, 'rgba4444'), S, S,
            { palette, delay: ALIVE_MS, transparent: true, dispose: 2 });
    }
    gif.finish();
    return Buffer.from(gif.bytes());
}

async function renderTwigiGif(config, size = 320) {
    const c = normalize(config);
    const g = galleryItem(c);
    if (g) { const out = renderGalleryGif(g, size); if (out) return out; }
    if (c.anim === 'alive') return renderAliveGif(c);
    const anim = c.anim === 'static' ? null : c.anim;
    const layers = buildLayers(c);
    let n = 1;
    if (anim) for (const l of layers) if (l.v.a?.[anim]) n = Math.max(n, l.v.a[anim][1]);
    const fps = (M.fps && M.fps[anim]) || 8;
    const ms = Math.round(1000 / fps);
    const up = upscaleFor(c.style);
    const gif = GIFEncoder();
    for (let i = 0; i < n; i++) {
        let rgba = up(compositeFrame(layers, anim, anim ? i : null), size);
        if (c.style === 'smooth') rgba = postSmoothF(rgba, size, false);
        for (let p = 3; p < rgba.length; p += 4) rgba[p] = rgba[p] < 128 ? 0 : 255;
        const palette = quantize(rgba, 256, { format: 'rgba4444', oneBitAlpha: true });
        gif.writeFrame(applyPalette(rgba, palette, 'rgba4444'), size, size,
            { palette, delay: ms, transparent: true, dispose: 2 });
    }
    gif.finish();
    return Buffer.from(gif.bytes());
}

function configHash(config) {
    return crypto.createHash('sha1').update(JSON.stringify(normalize(config))).digest('hex').slice(0, 16);
}

module.exports = { CATALOG, renderTwigi, renderTwigiGif, configHash, isAnimated };
