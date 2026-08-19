/**
 * The single source of truth for what a plan can control.
 *
 * The admin panel renders itself from this catalog, so adding a knob here is all it
 * takes for it to appear in the UI — no HTML edits, and the panel can never drift out
 * of sync with the server the way the hand-written one did (it was silently missing
 * maxLivePosts, liveTracking and all four tab toggles).
 *
 * `enforced` is deliberately honest about where — if anywhere — a knob actually bites:
 *   'server' — the API rejects the action, so it holds even against a patched client
 *   'app'    — the app hides or blocks it; a determined user could bypass it
 *   'none'   — declared but nothing reads it yet. Shown greyed out in the panel so
 *              nobody ships a plan believing a toggle does something it doesn't.
 */

const LIMITS = [
    { key: 'maxConnections',   label: 'Connections',        group: 'Core',      hint: 'People they can connect with.',            enforced: 'server' },
    { key: 'maxGroupMembers',  label: 'Group members',      group: 'Core',      hint: 'Max people in one group.',                 enforced: 'none'   },
    { key: 'maxStrokes',       label: 'Doodle strokes',     group: 'Creative',  hint: 'Strokes per scribble.',                    enforced: 'app'    },
    { key: 'maxReminders',     label: 'Reminders',          group: 'Reminders', hint: 'Active shared reminders.',                 enforced: 'server' },
    { key: 'maxCardsPerStack', label: 'Cards per stack',    group: 'Creative',  hint: 'Love cards in a single stack.',            enforced: 'app'    },
    { key: 'historyDays',      label: 'History (days)',     group: 'Core',      hint: 'How far back history is kept.',            enforced: 'app'    },
    { key: 'maxLivePosts',     label: 'Live posts',         group: 'Live',      hint: 'Open Live posts at once.',                 enforced: 'server' },
    { key: 'maxLiveRadiusM',   label: 'Live reach (m)',     group: 'Live',      hint: 'Largest radius they may pick, in metres.', enforced: 'server' }
];

const FEATURES = [
    // Navigation — the app hides a tab entirely when these are off.
    { key: 'tabReminders',    label: 'Reminders tab',       group: 'Navigation', hint: 'Show the Reminders tab.',                enforced: 'app'    },
    { key: 'tabLive',         label: 'Live tab',            group: 'Navigation', hint: 'Show the Live tab.',                     enforced: 'app'    },
    { key: 'tabSweetCorner',  label: 'Sweet Corner tab',    group: 'Navigation', hint: 'Show the Sweet Corner galaxy.',          enforced: 'app'    },
    { key: 'tabMusic',        label: 'Music tab',           group: 'Navigation', hint: 'Show the Music tab.',                    enforced: 'app'    },

    // Chat & sharing
    { key: 'gifPicker',       label: 'GIF picker',          group: 'Chat',       hint: 'Send GIFs in chat.',                     enforced: 'app'    },
    { key: 'animatedCards',   label: 'Animated cards',      group: 'Creative',   hint: 'Animated love cards.',                   enforced: 'none'   },
    { key: 'timeCapsule',     label: 'Time capsule',        group: 'Creative',   hint: 'Schedule a message far in the future.',  enforced: 'none'   },
    { key: 'premiumBrushes',  label: 'Premium brushes',     group: 'Creative',   hint: 'Extra doodle brushes.',                  enforced: 'none'   },

    // Connections
    { key: 'groupConnections', label: 'Groups',             group: 'Core',       hint: 'Create group connections.',              enforced: 'app'    },

    // Reminders
    { key: 'recurringAlarms', label: 'Recurring alarms',    group: 'Reminders',  hint: 'Repeat a reminder on a schedule.',       enforced: 'none'   },

    // Live
    { key: 'livePosting',     label: 'Post to Live',        group: 'Live',       hint: 'Create Live posts at all.',              enforced: 'server' },
    { key: 'liveTracking',    label: 'Live location',       group: 'Live',       hint: 'Share live location during a meet-up.',  enforced: 'server' },
    { key: 'liveFof',         label: 'Friends of friends',  group: 'Live',       hint: 'Widen a post beyond direct connections.', enforced: 'server' },

    // Look & feel
    { key: 'customTheme',     label: 'Custom theme',        group: 'Themes',     hint: 'Pick their own colours.',                enforced: 'none'   },
    { key: 'allThemes',       label: 'All themes',          group: 'Themes',     hint: 'Unlock every bundled theme.',            enforced: 'none'   }
];

/**
 * How a tier presents itself and what it costs.
 *
 * These used to be hardcoded in the app ("Plus", "Pro", "₹99/month" and the two Play
 * product ids), which is why deleting a tier here left the upgrade sheet still offering
 * it. The app now renders its upsell entirely from this metadata.
 */
const META = [
    { key: 'displayName', label: 'Display name', type: 'text',   default: '',  hint: 'Shown in the app. Defaults to the tier id.' },
    { key: 'emoji',       label: 'Badge',        type: 'text',   default: '✨', hint: 'One emoji.' },
    { key: 'tagline',     label: 'Tagline',      type: 'text',   default: '',  hint: 'One line under the name in the upgrade sheet.' },
    { key: 'priceLabel',  label: 'Price',        type: 'text',   default: '',  hint: 'e.g. ₹99/month. Play\'s localised price wins when available.' },
    { key: 'productId',   label: 'Product ID',   type: 'text',   default: '',  hint: 'Play Billing / gateway product id. Blank = not sellable.' },
    { key: 'purchasable', label: 'Sellable',     type: 'bool',   default: false, hint: 'Offer this tier as an upgrade in the app.' },
    { key: 'sortOrder',   label: 'Rank',         type: 'number', default: 0,   hint: 'Higher = better plan. Drives what counts as an upgrade.' }
];

const META_KEYS = META.map(m => m.key);
const NUMERIC_KEYS = LIMITS.map(l => l.key);
const FEATURE_KEYS = FEATURES.map(f => f.key);

/** Tabs default to on so a new/partial tier never accidentally hides the whole app. */
const DEFAULT_ON_FEATURES = new Set([
    'tabReminders', 'tabLive', 'tabSweetCorner', 'tabMusic', 'liveTracking', 'livePosting'
]);

const DEFAULT_TIER_PLANS = {
    free: {
        meta: {
            displayName: 'Free', emoji: '🌱', tagline: 'The basics, always',
            priceLabel: '', productId: '', purchasable: false, sortOrder: 0
        },
        maxConnections: 2, maxGroupMembers: 0, maxStrokes: 50, maxReminders: 5,
        maxCardsPerStack: 1, historyDays: 3, maxLivePosts: 1, maxLiveRadiusM: 2000,
        features: {
            gifPicker: false, premiumBrushes: false, timeCapsule: false, animatedCards: false,
            groupConnections: false, customTheme: false, allThemes: false, recurringAlarms: false,
            livePosting: true, liveTracking: true, liveFof: false,
            tabReminders: true, tabLive: true, tabSweetCorner: true, tabMusic: true
        }
    },
    plus: {
        meta: {
            displayName: 'Plus', emoji: '💎', tagline: 'More room for your people',
            priceLabel: '₹99/month', productId: 'gigi_plus_monthly',
            purchasable: true, sortOrder: 10
        },
        maxConnections: 5, maxGroupMembers: 8, maxStrokes: 200, maxReminders: 25,
        maxCardsPerStack: 5, historyDays: 30, maxLivePosts: 3, maxLiveRadiusM: 5000,
        features: {
            gifPicker: true, premiumBrushes: true, timeCapsule: false, animatedCards: true,
            groupConnections: true, customTheme: true, allThemes: false, recurringAlarms: true,
            livePosting: true, liveTracking: true, liveFof: true,
            tabReminders: true, tabLive: true, tabSweetCorner: true, tabMusic: true
        }
    },
    pro: {
        meta: {
            displayName: 'Pro', emoji: '👑', tagline: 'Everything, unlimited',
            priceLabel: '₹199/month', productId: 'gigi_pro_monthly',
            purchasable: true, sortOrder: 20
        },
        maxConnections: 0, maxGroupMembers: 50, maxStrokes: 1000, maxReminders: 200,
        maxCardsPerStack: 20, historyDays: 365, maxLivePosts: 0, maxLiveRadiusM: 10000,
        features: {
            gifPicker: true, premiumBrushes: true, timeCapsule: true, animatedCards: true,
            groupConnections: true, customTheme: true, allThemes: true, recurringAlarms: true,
            livePosting: true, liveTracking: true, liveFof: true,
            tabReminders: true, tabLive: true, tabSweetCorner: true, tabMusic: true
        }
    }
};

/** A plan with every gate open — what everyone gets when monetisation is switched off. */
function unlimitedPlan() {
    const out = { meta: {} };
    for (const m of META) out.meta[m.key] = m.default;
    out.meta.displayName = 'Everything';
    out.meta.emoji = '🎁';
    for (const k of NUMERIC_KEYS) out[k] = 0;      // 0 means unlimited everywhere
    out.features = {};
    for (const k of FEATURE_KEYS) out.features[k] = true;
    return out;
}

module.exports = {
    LIMITS, FEATURES, META, META_KEYS, NUMERIC_KEYS, FEATURE_KEYS,
    DEFAULT_ON_FEATURES, DEFAULT_TIER_PLANS, unlimitedPlan
};
