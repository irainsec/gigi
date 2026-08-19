/**
 * Live — nearby posts, joining, and live meet-up tracking.
 *
 * Kept in its own module (server.js is already ~6k lines). Mounted from server.js with
 * the pieces it needs injected, so it shares the same mongo connection, auth, sanitiser
 * and WebSocket fan-out rather than reinventing them.
 *
 * Design notes that matter:
 *  - A post's radius belongs to the POST, not the viewer. The nearby query therefore
 *    computes distance with $geoNear and then keeps posts where distance <= radiusM.
 *  - Exact coordinates are never sent to browsers. Feeds get `publicLocation`, fuzzed
 *    to a ~150m grid; the true point is revealed only to the author and accepted
 *    participants once a meet-up is live.
 *  - Location history is disposable: live_tracks has a 24h TTL, and Done purges early.
 *  - v1 audience is CONNECTIONS / FOF. The PUBLIC value exists in the schema so it can
 *    be switched on later without a migration, but it is rejected at the API for now.
 *
 * See plans/live_nearby_implementation.md
 */
const crypto = require('crypto');

// Radius and duration are continuous now — the client's dial can land on any
// value, so the server clamps to a range instead of matching a fixed list.
const MIN_RADIUS_M = 200;
const MAX_RADIUS_M = 10000;
const ALLOWED_VISIBILITY = ['CONNECTIONS', 'FOF'];      // PUBLIC intentionally excluded in v1
const ALLOWED_CATEGORIES = [
    'coffee', 'walk', 'food', 'study', 'sport', 'movie', 'help', 'other'
];
const CATEGORY_EMOJI = {
    coffee: '☕', walk: '🚶', food: '🍜', study: '📚',
    sport: '🏸', movie: '🎬', help: '🤝', other: '✨'
};
const MAX_OPEN_POSTS_PER_MEMBER = 3;
const FUZZ_GRID_M = 150;
const DEFAULT_DURATION_MIN = 120;
const MIN_DURATION_MIN = 5;
const MAX_DURATION_MIN = 300;

/** Snap+jitter a point so browsing users see a neighbourhood, never a doorstep. */
function fuzzPoint(lng, lat) {
    const mPerDegLat = 111_320;
    const mPerDegLng = 111_320 * Math.cos((lat * Math.PI) / 180) || 1;
    const grid = FUZZ_GRID_M;
    // deterministic-ish jitter so a post doesn't visibly jump between reads
    const jLat = ((Math.random() - 0.5) * grid) / mPerDegLat;
    const jLng = ((Math.random() - 0.5) * grid) / mPerDegLng;
    const snapLat = Math.round(lat / (grid / mPerDegLat)) * (grid / mPerDegLat);
    const snapLng = Math.round(lng / (grid / mPerDegLng)) * (grid / mPerDegLng);
    return [snapLng + jLng, snapLat + jLat];
}

/** Great-circle distance in metres. */
function haversineM(lat1, lng1, lat2, lng2) {
    const R = 6371000;
    const toRad = d => (d * Math.PI) / 180;
    const dLat = toRad(lat2 - lat1);
    const dLng = toRad(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

function isFiniteCoord(lat, lng) {
    return Number.isFinite(lat) && Number.isFinite(lng) &&
        lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
}

module.exports = function mountLiveRoutes(ctx) {
    const {
        app, gigiConn, mongoose, sanitizeText, requireAuthenticatedMember,
        ConnectionMembership, Member, broadcastToConnection, normalizeId, logEvent,
        resolvePlanForMember, getAppSettings, admin
    } = ctx;

    /**
     * Push to a set of members. Socket events only reach people with the app open, and
     * "someone's free near you" is worthless if it waits until you happen to look.
     */
    async function pushTo(memberIds, { title, body, data }) {
        if (!admin || !memberIds?.length) return;
        const rows = await Member.find({
            memberId: { $in: memberIds }, fcmToken: { $ne: null }
        }).select('memberId fcmToken').lean();

        await Promise.all(rows.map(async row => {
            try {
                await admin.messaging().send({
                    token: row.fcmToken,
                    data: Object.fromEntries(
                        Object.entries(data || {}).map(([k, v]) => [k, String(v ?? '')])
                    ),
                    android: {
                        priority: 'high',
                        notification: {
                            title, body,
                            channelId: 'scribble_alerts_v3',
                            sound: 'default'
                        }
                    }
                });
            } catch (e) {
                if (e.code === 'messaging/registration-token-not-registered') {
                    await Member.updateOne(
                        { memberId: row.memberId }, { $set: { fcmToken: null } }
                    );
                }
            }
        }));
    }

    /**
     * Of the post's audience, only those actually inside its radius right now — the
     * whole point of picking a radius is not pinging people three suburbs away.
     * Uses each member's last known Live position, so anyone we've never seen a
     * location for simply isn't notified.
     */
    async function audienceInRange(post) {
        const ids = (post.audienceMemberIds || []).map(String);
        if (!ids.length) return [];
        const [lng, lat] = post.location.coordinates;
        const recent = await LiveTrack.aggregate([
            { $match: { memberId: { $in: ids } } },
            { $sort: { at: -1 } },
            { $group: { _id: '$memberId', loc: { $first: '$loc' } } }
        ]);
        return recent
            .filter(r => {
                const c = r.loc?.coordinates;
                if (!c) return false;
                return haversineM(lat, lng, c[1], c[0]) <= post.radiusM;
            })
            .map(r => String(r._id));
    }

    /** Plan + global kill switches for one member. Falls open if either is missing. */
    async function gate(member) {
        const plan = resolvePlanForMember ? await resolvePlanForMember(member) : null;
        const settings = getAppSettings ? await getAppSettings() : {};
        return { plan, settings };
    }

    // ── schemas ──────────────────────────────────────────────────────────────
    const PointSchema = new mongoose.Schema({
        type: { type: String, enum: ['Point'], default: 'Point' },
        coordinates: { type: [Number], required: true }        // [lng, lat]
    }, { _id: false });

    const LivePostSchema = new mongoose.Schema({
        postId: { type: String, unique: true, index: true },
        authorMemberId: { type: String, index: true },
        authorName: String,
        authorAvatarUrl: String,
        text: String,
        category: { type: String, default: 'other' },
        mood: String,
        location: PointSchema,           // exact — never leaves the server unredacted
        publicLocation: PointSchema,     // fuzzed — what feeds see
        placeLabel: String,
        radiusM: { type: Number, default: 500 },
        visibility: { type: String, default: 'CONNECTIONS' },
        audienceMemberIds: { type: [String], default: [], index: true },
        maxJoiners: { type: Number, default: null },
        acceptedMemberIds: { type: [String], default: [] },
        meetupGroupCode: { type: String, default: null },
        startsAt: Date,
        expiresAt: { type: Date, index: true },
        status: { type: String, default: 'OPEN', index: true },
        doneAt: Date,
        createdAt: { type: Date, default: Date.now }
    });
    LivePostSchema.index({ location: '2dsphere' });
    LivePostSchema.index({ publicLocation: '2dsphere' });

    const LiveJoinSchema = new mongoose.Schema({
        postId: { type: String, index: true },
        memberId: { type: String, index: true },
        name: String,
        avatarUrl: String,
        note: String,
        status: { type: String, default: 'PENDING' },   // PENDING|ACCEPTED|DECLINED|LEFT
        createdAt: { type: Date, default: Date.now }
    });
    LiveJoinSchema.index({ postId: 1, memberId: 1 }, { unique: true });

    const LiveTrackSchema = new mongoose.Schema({
        postId: { type: String, index: true },
        memberId: String,
        loc: PointSchema,
        heading: Number,
        speed: Number,
        battery: Number,
        at: { type: Date, default: Date.now }
    });
    // location history is disposable — never keep it beyond a day
    LiveTrackSchema.index({ at: 1 }, { expireAfterSeconds: 86400 });

    const LivePost = gigiConn.model('LivePost', LivePostSchema);
    const LiveJoin = gigiConn.model('LiveJoinRequest', LiveJoinSchema);
    const LiveTrack = gigiConn.model('LiveTrack', LiveTrackSchema);

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Everyone allowed to see this member's posts: their connections (+ FoF). */
    async function audienceFor(memberId, includeFof) {
        // Exclude NEBULA-origin connections with trustRing >= 3 (Trust Ladder security rule)
        const myCodes = await ConnectionMembership
            .find({
                memberId,
                archivedAt: null,
                $or: [
                    { origin: { $ne: 'NEBULA' } },
                    { trustRing: { $lt: 3 } }
                ]
            }).distinct('connectionCode');
        const firstDegree = await ConnectionMembership
            .find({
                connectionCode: { $in: myCodes },
                archivedAt: null,
                $or: [
                    { origin: { $ne: 'NEBULA' } },
                    { trustRing: { $lt: 3 } }
                ]
            })
            .distinct('memberId');
        const set = new Set(firstDegree.filter(Boolean).map(String));
        set.delete(String(memberId));
        if (includeFof && set.size) {
            const theirCodes = await ConnectionMembership
                .find({ memberId: { $in: [...set] }, archivedAt: null })
                .distinct('connectionCode');
            const second = await ConnectionMembership
                .find({ connectionCode: { $in: theirCodes }, archivedAt: null })
                .distinct('memberId');
            second.filter(Boolean).forEach(m => set.add(String(m)));
            set.delete(String(memberId));
        }
        return [...set];
    }

    /**
     * Everyone who's actually in this meet-up, host first. Avatars come along so the
     * feed and the map can show real faces instead of anonymous dots.
     */
    async function participantsFor(post) {
        const ids = (post.acceptedMemberIds || []).map(String);
        const rows = ids.length
            ? await Member.find({ memberId: { $in: ids } })
                .select('memberId displayName avatarMode twigiRenderUrl profileEmojiUrl')
                .lean()
            : [];
        const shape = m => ({
            memberId: m.memberId,
            name: m.displayName || 'Someone',
            avatarUrl: (m.avatarMode === 'TWIGI' && m.twigiRenderUrl)
                ? m.twigiRenderUrl : (m.profileEmojiUrl || null)
        });
        return [
            { memberId: post.authorMemberId, name: post.authorName, avatarUrl: post.authorAvatarUrl || null, isHost: true },
            ...rows.map(shape)
        ];
    }

    /** Strip anything the caller isn't entitled to see. */
    function shapePost(post, viewerId, distanceM, participants) {
        const isAuthor = String(post.authorMemberId) === String(viewerId);
        const isParticipant = (post.acceptedMemberIds || []).map(String).includes(String(viewerId));
        const precise = isAuthor || isParticipant;
        const coords = precise
            ? post.location?.coordinates
            : post.publicLocation?.coordinates;
        return {
            postId: post.postId,
            authorMemberId: post.authorMemberId,
            authorName: post.authorName,
            authorAvatarUrl: post.authorAvatarUrl || null,
            text: post.text,
            category: post.category,
            mood: post.mood || null,
            lat: coords?.[1] ?? null,
            lng: coords?.[0] ?? null,
            preciseLocation: precise,
            placeLabel: post.placeLabel || null,
            radiusM: post.radiusM,
            visibility: post.visibility,
            status: post.status,
            maxJoiners: post.maxJoiners,
            acceptedCount: (post.acceptedMemberIds || []).length,
            isFull: !!(post.maxJoiners && (post.acceptedMemberIds || []).length >= post.maxJoiners),
            participants: participants || [],
            meetupGroupCode: precise ? post.meetupGroupCode : null,
            isMine: isAuthor,
            distanceM: Number.isFinite(distanceM) ? Math.round(distanceM) : null,
            startsAt: post.startsAt, expiresAt: post.expiresAt, createdAt: post.createdAt
        };
    }

    async function notifyAudience(post, payloadBuilder) {
        // Live posts fan out over the author's connection rooms — reuses the socket
        // rooms the app is already subscribed to, so no new subscription plumbing.
        const codes = await ConnectionMembership
            .find({ memberId: post.authorMemberId, archivedAt: null })
            .distinct('connectionCode');
        codes.forEach(code => {
            try { broadcastToConnection(String(code).toLowerCase(), payloadBuilder); }
            catch (_) { /* best effort */ }
        });
    }

    // ── routes ───────────────────────────────────────────────────────────────

    /** Create a post. Location is captured one-shot by the client. */
    app.post('/api/live/posts', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;

            const text = sanitizeText(req.body?.text || '', 180);
            if (!text) return res.status(400).json({ error: 'Say what you are up to.' });

            const lat = Number(req.body?.lat), lng = Number(req.body?.lng);
            if (!isFiniteCoord(lat, lng)) return res.status(400).json({ error: 'Bad location.' });

            const rawRadius = Math.round(Number(req.body?.radiusM));
            const radiusM = Number.isFinite(rawRadius)
                ? Math.min(MAX_RADIUS_M, Math.max(MIN_RADIUS_M, rawRadius))
                : 500;
            const visibility = ALLOWED_VISIBILITY.includes(req.body?.visibility)
                ? req.body.visibility : 'CONNECTIONS';
            const category = ALLOWED_CATEGORIES.includes(req.body?.category)
                ? req.body.category : 'other';

            const { plan, settings } = await gate(me);
            if (settings?.killLive) {
                return res.status(503).json({ error: 'Live is temporarily unavailable.' });
            }
            if (plan && plan.features?.livePosting === false) {
                return res.status(402).json({
                    error: 'Posting to Live is not part of your plan.',
                    code: 'PLAN_LIMIT_REACHED'
                });
            }
            if (visibility === 'FOF' && plan && plan.features?.liveFof === false) {
                return res.status(402).json({
                    error: 'Sharing beyond your connections is not part of your plan.',
                    code: 'PLAN_LIMIT_REACHED'
                });
            }
            // A plan cap of 0 means unlimited, matching every other limit in the app.
            const planRadiusCap = Number(plan?.maxLiveRadiusM);
            if (Number.isFinite(planRadiusCap) && planRadiusCap > 0 && radiusM > planRadiusCap) {
                return res.status(402).json({
                    error: `Your plan reaches up to ${planRadiusCap} m.`,
                    code: 'PLAN_LIMIT_REACHED'
                });
            }

            const openCap = Number.isFinite(Number(plan?.maxLivePosts))
                ? Number(plan.maxLivePosts) : MAX_OPEN_POSTS_PER_MEMBER;
            if (openCap > 0) {
                const open = await LivePost.countDocuments({
                    authorMemberId: me.memberId,
                    status: { $in: ['OPEN', 'ACTIVE'] },
                    expiresAt: { $gt: new Date() }
                });
                if (open >= openCap) {
                    return res.status(402).json({
                        error: `Your plan allows ${openCap} open post${openCap === 1 ? '' : 's'} at a time.`,
                        code: 'PLAN_LIMIT_REACHED'
                    });
                }
            }

            const durationMin = Math.min(
                MAX_DURATION_MIN,
                Math.max(MIN_DURATION_MIN, Number(req.body?.durationMin) || DEFAULT_DURATION_MIN)
            );
            const startsAt = req.body?.startsAt ? new Date(req.body.startsAt) : new Date();
            const expiresAt = new Date(startsAt.getTime() + durationMin * 60_000);

            const post = await LivePost.create({
                postId: crypto.randomUUID(),
                authorMemberId: me.memberId,
                authorName: sanitizeText(me.displayName || 'Someone', 80),
                authorAvatarUrl: (me.avatarMode === 'TWIGI' && me.twigiRenderUrl)
                    ? me.twigiRenderUrl : (me.profileEmojiUrl || null),
                text, category,
                mood: sanitizeText(req.body?.mood || '', 8) || null,
                location: { type: 'Point', coordinates: [lng, lat] },
                publicLocation: { type: 'Point', coordinates: fuzzPoint(lng, lat) },
                placeLabel: sanitizeText(req.body?.placeLabel || '', 80) || null,
                radiusM, visibility,
                audienceMemberIds: await audienceFor(me.memberId, visibility === 'FOF'),
                maxJoiners: Number(req.body?.maxJoiners) > 0 ? Number(req.body.maxJoiners) : null,
                startsAt, expiresAt, status: 'OPEN'
            });

            const summary = shapePost(post, null, null);
            await notifyAudience(post, () => ({ type: 'live_post_new', post: summary }));
            logEvent?.('live.post.created', { memberId: me.memberId, radiusM, visibility });

            // Fire-and-forget: a slow push must never hold up the create response.
            audienceInRange(post)
                .then(ids => pushTo(ids, {
                    title: `${post.authorName} is free nearby ${CATEGORY_EMOJI[post.category] || '✨'}`,
                    body: post.text,
                    data: { type: 'live_post_new', postId: post.postId }
                }))
                .catch(e => console.error('[live] nearby push failed:', e.message));
            res.json({ ok: true, post: shapePost(post, me.memberId, 0, await participantsFor(post)) });
        } catch (e) {
            console.error('[live] create failed:', e.message);
            res.status(500).json({ error: 'Could not go live.' });
        }
    });

    /**
     * Nearby feed. The radius belongs to the post, so we measure distance first and
     * then keep only posts whose OWN radius reaches the viewer.
     */
    app.get('/api/live/posts', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;
            const lat = Number(req.query.lat), lng = Number(req.query.lng);
            if (!isFiniteCoord(lat, lng)) return res.status(400).json({ error: 'Bad location.' });

            const posts = await LivePost.aggregate([
                {
                    $geoNear: {
                        near: { type: 'Point', coordinates: [lng, lat] },
                        // The collection carries two 2dsphere indexes, so $geoNear
                        // needs to be told which one — omitting `key` is an error.
                        // Distance is measured from the TRUE point; only the
                        // coordinates we hand back are fuzzed.
                        key: 'location',
                        distanceField: 'distanceM',
                        maxDistance: MAX_RADIUS_M,
                        spherical: true,
                        query: {
                            status: { $in: ['OPEN', 'ACTIVE', 'FULL'] },
                            expiresAt: { $gt: new Date() },
                            $or: [
                                { audienceMemberIds: me.memberId },
                                { authorMemberId: me.memberId }
                            ]
                        }
                    }
                },
                {
                    // The radius decides who ELSE can see a post. Applying it to the
                    // author too meant that drifting further than your own radius —
                    // trivial at 200 m, and likelier now the client seeds from the last
                    // known position — made your own post disappear on you while it was
                    // still live for everybody else.
                    $match: {
                        $expr: {
                            $or: [
                                { $eq: ['$authorMemberId', me.memberId] },
                                { $lte: ['$distanceM', '$radiusM'] }
                            ]
                        }
                    }
                },
                { $sort: { startsAt: 1 } },
                { $limit: 100 }
            ]);

            // One Member lookup per post. The feed is capped at 100 and most posts
            // have no accepted joiners yet (participantsFor short-circuits on an empty
            // list), so this stays cheap; batch it if the cap ever rises.
            const shaped = await Promise.all(
                posts.map(async p => shapePost(p, me.memberId, p.distanceM, await participantsFor(p)))
            );
            res.json({ posts: shaped });
        } catch (e) {
            console.error('[live] feed failed:', e.message);
            res.status(500).json({ error: 'Could not load Live.' });
        }
    });

    /** Ask to join. The author must approve before any location is shared. */
    app.post('/api/live/posts/:id/join', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;
            const post = await LivePost.findOne({ postId: req.params.id });
            if (!post || post.status === 'DONE' || post.expiresAt < new Date()) {
                return res.status(404).json({ error: 'That plan is no longer live.' });
            }
            if (String(post.authorMemberId) === String(me.memberId)) {
                return res.status(400).json({ error: "It's your own plan." });
            }
            let allowed = (post.audienceMemberIds || []).map(String).includes(String(me.memberId));
            if (!allowed) {
                const authorCodes = await ConnectionMembership.find({ memberId: post.authorMemberId, archivedAt: null }).distinct('connectionCode');
                const myCodes = await ConnectionMembership.find({ memberId: me.memberId, archivedAt: null }).distinct('connectionCode');
                allowed = authorCodes.some(c => myCodes.includes(c));
            }
            if (!allowed) return res.status(403).json({ error: 'Not visible to you.' });

            // Capacity is checked here as well as at accept time: without this, ten
            // people can queue up requests for three spots and the host has to
            // disappoint seven of them by hand.
            const alreadyIn = (post.acceptedMemberIds || []).map(String);
            if (post.maxJoiners && alreadyIn.length >= post.maxJoiners &&
                !alreadyIn.includes(String(me.memberId))) {
                return res.status(409).json({ error: 'This one is full.', code: 'LIVE_FULL' });
            }

            await LiveJoin.findOneAndUpdate(
                { postId: post.postId, memberId: me.memberId },
                {
                    postId: post.postId, memberId: me.memberId,
                    name: sanitizeText(me.displayName || 'Someone', 80),
                    avatarUrl: (me.avatarMode === 'TWIGI' && me.twigiRenderUrl)
                        ? me.twigiRenderUrl : (me.profileEmojiUrl || null),
                    note: sanitizeText(req.body?.note || '', 120),
                    status: 'PENDING'
                },
                { upsert: true, new: true }
            );

            const joinerName = sanitizeText(me.displayName || 'Someone', 80);
            await notifyAudience(post, () => ({
                type: 'live_join_request',
                postId: post.postId,
                memberId: me.memberId,
                name: joinerName
            }));
            pushTo([post.authorMemberId], {
                title: `${joinerName} wants to join 🙋`,
                body: post.text,
                data: { type: 'live_join_request', postId: post.postId, memberId: me.memberId }
            }).catch(e => console.error('[live] join push failed:', e.message));
            res.json({ ok: true, status: 'PENDING' });
        } catch (e) {
            console.error('[live] join failed:', e.message);
            res.status(500).json({ error: 'Could not ask to join.' });
        }
    });

    /** Author accepts / declines a joiner. Acceptance is what unlocks live location. */
    app.post('/api/live/posts/:id/respond', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;
            const post = await LivePost.findOne({ postId: req.params.id });
            if (!post) return res.status(404).json({ error: 'Not found.' });
            if (String(post.authorMemberId) !== String(me.memberId)) {
                return res.status(403).json({ error: 'Only the host can do that.' });
            }
            const memberId = sanitizeText(req.body?.memberId || '', 80);
            const accept = Boolean(req.body?.accept);

            await LiveJoin.updateOne(
                { postId: post.postId, memberId },
                { status: accept ? 'ACCEPTED' : 'DECLINED' }
            );

            if (accept) {
                const accepted = new Set((post.acceptedMemberIds || []).map(String));
                accepted.add(String(memberId));
                post.acceptedMemberIds = [...accepted];
                post.status = (post.maxJoiners && accepted.size >= post.maxJoiners)
                    ? 'FULL' : 'ACTIVE';
                await post.save();
            }

            await notifyAudience(post, () => ({
                type: accept ? 'live_join_accepted' : 'live_join_declined',
                postId: post.postId,
                memberId,
                meetupGroupCode: post.meetupGroupCode || null
            }));
            res.json({ ok: true });
        } catch (e) {
            console.error('[live] respond failed:', e.message);
            res.status(500).json({ error: 'Could not respond.' });
        }
    });

    /** Position ping from an accepted participant — relayed, then stored briefly. */
    /** Everything this member has ever posted, newest first — the Live history log. */
    app.get('/api/live/mine', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;

            const rows = await LivePost.find({ authorMemberId: me.memberId })
                .sort({ createdAt: -1 })
                .limit(50)
                .lean();

            const now = new Date();
            const posts = await Promise.all(rows.map(async p => {
                const shaped = shapePost(p, me.memberId, null, await participantsFor(p));
                // Surface WHY something is no longer live, so an expired post reads as
                // expired rather than as a bug.
                shaped.endedReason =
                    p.status === 'DONE' ? 'done'
                    : p.expiresAt && p.expiresAt < now ? 'expired'
                    : p.status === 'CANCELLED' ? 'cancelled'
                    : null;
                shaped.isLive = !shaped.endedReason;
                shaped.createdAt = p.createdAt ? new Date(p.createdAt).toISOString() : null;
                return shaped;
            }));

            res.json({ posts });
        } catch (e) {
            console.error('[live] history failed:', e.message);
            res.status(500).json({ error: 'Could not load your Live history.' });
        }
    });

    /** Author deletes a post outright, taking its join requests and trail with it. */
    app.delete('/api/live/posts/:id', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;

            const post = await LivePost.findOne({ postId: req.params.id });
            if (!post) return res.status(404).json({ error: 'Already gone.' });
            if (String(post.authorMemberId) !== String(me.memberId)) {
                return res.status(403).json({ error: 'Not yours to delete.' });
            }

            // Tell anyone watching before the record disappears.
            if (post.status !== 'DONE') {
                await notifyAudience(post, () => ({
                    type: 'live_post_done', postId: post.postId
                }));
            }

            await Promise.all([
                LivePost.deleteOne({ postId: post.postId }),
                LiveJoin.deleteMany({ postId: post.postId }),
                LiveTrack.deleteMany({ postId: post.postId })
            ]);

            logEvent?.('live.post.deleted', { memberId: me.memberId, postId: post.postId });
            res.json({ ok: true });
        } catch (e) {
            console.error('[live] delete failed:', e.message);
            res.status(500).json({ error: 'Could not delete that.' });
        }
    });

    app.post('/api/live/track', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;
            const postId = sanitizeText(req.body?.postId || '', 80);
            const lat = Number(req.body?.lat), lng = Number(req.body?.lng);
            if (!postId || !isFiniteCoord(lat, lng)) {
                return res.status(400).json({ error: 'Bad payload.' });
            }
            const post = await LivePost.findOne({ postId });
            if (!post || post.status === 'DONE' || post.expiresAt < new Date()) {
                // tell the client to stop its foreground service
                return res.status(410).json({ error: 'Meet-up finished.', stop: true });
            }
            // `stop: true` tells the client's foreground service to shut down, so a
            // kill switch or plan change ends live sharing on the next ping.
            const { plan: trackPlan, settings: trackSettings } = await gate(me);
            if (trackSettings?.killLive || trackSettings?.killLiveTracking) {
                return res.status(503).json({ error: 'Location sharing is paused.', stop: true });
            }
            if (trackPlan && trackPlan.features?.liveTracking === false) {
                return res.status(402).json({
                    error: 'Live location is not part of your plan.',
                    code: 'PLAN_LIMIT_REACHED', stop: true
                });
            }

            const isAuthor = String(post.authorMemberId) === String(me.memberId);
            const isAccepted = (post.acceptedMemberIds || []).map(String).includes(String(me.memberId));
            if (!isAuthor && !isAccepted) {
                return res.status(403).json({ error: 'Not part of this meet-up.', stop: true });
            }

            await LiveTrack.create({
                postId, memberId: me.memberId,
                loc: { type: 'Point', coordinates: [lng, lat] },
                heading: Number(req.body?.heading) || null,
                speed: Number(req.body?.speed) || null,
                battery: Number(req.body?.battery) || null
            });

            await notifyAudience(post, () => ({
                type: 'live_location',
                postId, memberId: me.memberId,
                name: sanitizeText(me.displayName || 'Someone', 80),
                avatarUrl: (me.avatarMode === 'TWIGI' && me.twigiRenderUrl)
                    ? me.twigiRenderUrl : (me.profileEmojiUrl || null),
                lat, lng,
                heading: Number(req.body?.heading) || null
            }));
            res.json({ ok: true });
        } catch (e) {
            console.error('[live] track failed:', e.message);
            res.status(500).json({ error: 'Could not update location.' });
        }
    });

    /** Host closes it. Tracking stops everywhere and the breadcrumbs are deleted now. */
    app.post('/api/live/posts/:id/done', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;
            const post = await LivePost.findOne({ postId: req.params.id });
            if (!post) return res.status(404).json({ error: 'Not found.' });
            if (String(post.authorMemberId) !== String(me.memberId)) {
                return res.status(403).json({ error: 'Only the host can finish it.' });
            }
            post.status = 'DONE';
            post.doneAt = new Date();
            await post.save();
            await LiveTrack.deleteMany({ postId: post.postId });   // don't wait for the TTL

            await notifyAudience(post, () => ({ type: 'live_post_done', postId: post.postId }));
            logEvent?.('live.post.done', { memberId: me.memberId });
            res.json({ ok: true });
        } catch (e) {
            console.error('[live] done failed:', e.message);
            res.status(500).json({ error: 'Could not finish.' });
        }
    });

    /** Leave a meet-up you joined (stops your sharing, keeps the post alive). */
    app.post('/api/live/posts/:id/leave', async (req, res) => {
        try {
            const auth = await requireAuthenticatedMember(req, res);
            if (!auth) return;
            const me = auth.member;
            const post = await LivePost.findOne({ postId: req.params.id });
            if (!post) return res.status(404).json({ error: 'Not found.' });
            post.acceptedMemberIds = (post.acceptedMemberIds || [])
                .filter(m => String(m) !== String(me.memberId));
            if (post.status === 'FULL') post.status = 'ACTIVE';
            await post.save();
            await LiveJoin.updateOne({ postId: post.postId, memberId: me.memberId }, { status: 'LEFT' });
            await LiveTrack.deleteMany({ postId: post.postId, memberId: me.memberId });
            await notifyAudience(post, () => ({
                type: 'live_participant_left', postId: post.postId, memberId: me.memberId
            }));
            res.json({ ok: true });
        } catch (e) {
            console.error('[live] leave failed:', e.message);
            res.status(500).json({ error: 'Could not leave.' });
        }
    });

    console.log('[live] routes mounted');
    return { LivePost, LiveJoin, LiveTrack };
};
