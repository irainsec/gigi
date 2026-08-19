/**
 * Remote app settings — things the app used to have baked in at build time that can
 * now be changed from the admin panel and take effect on the next launch (or instantly
 * over the socket), with no APK release.
 *
 * Two rules kept this honest:
 *  - Secrets live here, not in the repo. `secret: true` fields are masked in the admin
 *    GET response and only overwritten when a new value is actually submitted, so
 *    saving the form never blanks a key you can't see.
 *  - Nothing here is sent to the app unless `client: true`. Server-only values (like
 *    an outbound SMS key) must never end up in a client payload.
 */

const SETTINGS_CATALOG = [
    // ── keys the app needs ────────────────────────────────────────────────────
    {
        key: 'giphyApiKey', label: 'Giphy API key', group: 'API keys', type: 'text',
        secret: true, client: true, default: '',
        hint: 'Powers the GIF tray in chat. Blank disables the picker.'
    },
    {
        key: 'spotifyClientId', label: 'Spotify client ID', group: 'API keys', type: 'text',
        client: true, default: '',
        hint: "From developer.spotify.com/dashboard. Not a secret — the app uses PKCE, so there is no client secret. Blank hides the Connect Spotify button. Register redirect URI gigi://spotify-callback."
    },
    {
        key: 'osmTileUrl', label: 'Map tile URL', group: 'API keys', type: 'text',
        client: true, default: 'https://gigi.iamanraj.com/tiles/{z}/{x}/{y}.png',
        hint: "Tile template for Live's maps. Defaults to our own caching proxy; paste a provider URL to switch."
    },

    // ── release control ───────────────────────────────────────────────────────
    {
        key: 'minSupportedVersionCode', label: 'Minimum version', group: 'Releases', type: 'number',
        client: true, default: 0,
        hint: 'Builds below this are asked to update before continuing. 0 = no floor.'
    },
    {
        key: 'forceUpdate', label: 'Force the update', group: 'Releases', type: 'bool',
        client: true, default: false,
        hint: 'Make the update prompt non-dismissible for builds under the minimum.'
    },

    // ── operations ────────────────────────────────────────────────────────────
    {
        key: 'maintenanceMode', label: 'Maintenance mode', group: 'Operations', type: 'bool',
        client: true, default: false,
        hint: 'Show a "we\'ll be right back" banner in the app.'
    },
    {
        key: 'announcement', label: 'Announcement', group: 'Operations', type: 'text',
        client: true, default: '',
        hint: 'A short message shown in-app. Leave blank for none.'
    },

    // ── monetisation ──────────────────────────────────────────────────────────
    {
        key: 'freeForAll', label: 'Everything free for everyone', group: 'Monetisation', type: 'bool',
        client: true, default: false,
        hint: 'Ignores plans entirely: every limit unlimited, every feature on, and the ' +
              'app stops offering upgrades. Existing subscriptions are untouched — ' +
              'switch it back off and tiers apply again exactly as before.'
    },

    // ── global kill switches, independent of plans ────────────────────────────
    {
        key: 'killReminders', label: 'Disable Reminders', group: 'Kill switches', type: 'bool',
        client: true, default: false,
        hint: 'Hide the Reminders tab from bottom navigation for everyone.'
    },
    {
        key: 'killLive', label: 'Disable Live', group: 'Kill switches', type: 'bool',
        client: true, default: false,
        hint: 'Hide the Live tab from bottom navigation for everyone.'
    },
    {
        key: 'killSweetCorner', label: 'Disable Sweet Corner', group: 'Kill switches', type: 'bool',
        client: true, default: false,
        hint: 'Hide the Sweet Corner / Galaxy tab from bottom navigation for everyone.'
    },
    {
        key: 'killMusic', label: 'Disable Music', group: 'Kill switches', type: 'bool',
        client: true, default: false,
        hint: 'Hide the Music tab from bottom navigation for everyone.'
    },
    {
        key: 'killLiveTracking', label: 'Disable live location', group: 'Kill switches', type: 'bool',
        client: true, default: false,
        hint: 'Stops all location sharing without disabling the rest of Live.'
    },
    {
        key: 'killNest', label: 'Disable Our Nest', group: 'Kill switches', type: 'bool',
        client: true, default: false,
        hint: 'Hide the Our Nest cozy room tab from bottom navigation for everyone.'
    },
    {
        key: 'killCosmicNebula', label: 'Disable Cosmic Nebula', group: 'Kill switches', type: 'bool',
        client: true, default: false,
        hint: 'Hide the Cosmic Nebula public discovery universe for everyone.'
    },

    // ── links ─────────────────────────────────────────────────────────────────
    {
        key: 'supportEmail', label: 'Support email', group: 'Links', type: 'text',
        client: true, default: 'aman.raj@alticyber.com', hint: 'Shown on the help screen.'
    },
    {
        key: 'privacyUrl', label: 'Privacy policy URL', group: 'Links', type: 'text',
        client: true, default: 'https://gigi.iamanraj.com/privacy', hint: ''
    }
];

const BY_KEY = new Map(SETTINGS_CATALOG.map(s => [s.key, s]));

function defaults() {
    const out = {};
    for (const s of SETTINGS_CATALOG) out[s.key] = s.default;
    return out;
}

/** Coerce one submitted value to its declared type. Returns undefined to skip it. */
function coerce(spec, raw) {
    if (spec.type === 'bool') return raw === true || raw === 'true';
    if (spec.type === 'number') {
        const n = Number(raw);
        return Number.isFinite(n) ? n : undefined;
    }
    if (typeof raw !== 'string') return undefined;
    // A masked secret coming back unchanged means "leave it alone".
    if (spec.secret && raw === MASK) return undefined;
    return raw.trim().slice(0, 2000);
}

const MASK = '••••••••';

/** What the admin panel sees — secrets replaced by a mask. */
function forAdmin(stored) {
    const merged = { ...defaults(), ...(stored || {}) };
    const out = {};
    for (const s of SETTINGS_CATALOG) {
        out[s.key] = s.secret && merged[s.key] ? MASK : merged[s.key];
    }
    return out;
}

/** What the app sees — client-visible keys only. */
function forClient(stored) {
    const merged = { ...defaults(), ...(stored || {}) };
    const out = {};
    for (const s of SETTINGS_CATALOG) {
        if (s.client) out[s.key] = merged[s.key];
    }
    return out;
}

/** Apply a submitted patch onto the stored object, ignoring unknown/invalid keys. */
function applyPatch(stored, patch) {
    const next = { ...defaults(), ...(stored || {}) };
    for (const [k, v] of Object.entries(patch || {})) {
        const spec = BY_KEY.get(k);
        if (!spec) continue;
        const val = coerce(spec, v);
        if (val !== undefined) next[k] = val;
    }
    return next;
}

module.exports = { SETTINGS_CATALOG, defaults, forAdmin, forClient, applyPatch, MASK };
