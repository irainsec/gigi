# Live — detailed implementation plan (connections-first)

> Companion to `live_nearby_plan.md` (which covers *why*). This document is the *how*:
> schemas, endpoints, files, screens, and phase-by-phase deliverables.
> **Decision taken:** ship to **connections + friends-of-friends** first; `visibility`
> field is built in from day one so `PUBLIC` can be switched on later without migration.
> Status: **plan only, nothing built.** Written 2026-07-27.

---

## 1. The feature in one paragraph

A fifth tab, **Live 📍**. You post what you're doing ("chai + rant ☕", "gym in 30 min"),
pick who can see it and how far (200 m / 500 m / 1 km / 5 km), and it appears on the
map + feed of your people who are inside that radius. They tap **I'm in**, you approve,
and the meet-up goes **live**: everyone's position streams onto a shared map with ETAs,
plus an auto-created Gigi group with chat, doodles and break cards. Tap **Done** and it
vanishes — tracks deleted, everyone unsubscribed.

---

## 2. Architecture at a glance

```
Android                              Server (gigi-server)              Mongo
────────                             ────────────────────              ─────
LiveScreen (feed + map)   ──REST──►  /api/live/*                 ──►   live_posts (2dsphere)
LiveMapScreen             ◄──WS───   live_* message types              live_join_requests
LiveLocationService (FGS) ──WS───►   live_track relay                  live_tracks (TTL 24h)
LiveRepository / Room     ◄─cache─                                     live_meetups
```

Reuses what already exists:
- **WebSocket** — `ScribbleSyncManager` + `broadcastToConnection()` pattern
- **HTTP client** — `ConnectionBootstrapManager.postJson` style
- **Groups + chat + doodle + break** — a meet-up *becomes* a temporary Gigi group
- **Room** — `ScreensaverDatabase` (needs v26 migration)

---

## 3. Data model

### 3.1 Server (Mongoose)

```js
// live_posts
{
  postId: String,                      // uuid
  authorMemberId: String,
  authorName: String,
  authorAvatarUrl: String,             // emoji or Twigi render
  text: String,                        // max 180
  category: String,                    // coffee|walk|food|study|sport|movie|help|other
  mood: String,                        // optional emoji

  location:       { type: "Point", coordinates: [lng, lat] },  // exact, never sent raw
  publicLocation: { type: "Point", coordinates: [lng, lat] },  // ~150m fuzzed, what feeds show
  placeLabel: String,                  // "near Koregaon Park" — reverse geocoded, coarse

  radiusM: Number,                     // 200 | 500 | 1000 | 5000
  visibility: String,                  // CONNECTIONS | FOF | PUBLIC  (PUBLIC gated off in v1)
  audienceMemberIds: [String],         // denormalised snapshot of who may see it

  maxJoiners: Number,                  // null = unlimited
  acceptedMemberIds: [String],
  meetupGroupCode: String,             // auto-created Gigi group once someone joins

  startsAt: Date,                      // now, or scheduled
  expiresAt: Date,                     // TTL index
  status: String,                      // OPEN | FULL | ACTIVE | DONE | CANCELLED | EXPIRED
  doneAt: Date, createdAt: Date
}

// live_join_requests { postId, memberId, name, avatarUrl, note, status, createdAt }
//   status: PENDING | ACCEPTED | DECLINED | LEFT

// live_tracks { postId, memberId, loc: Point, heading, speed, battery, at }
//   TTL index on `at`, 24h — location history never lingers

// live_reputation { memberId, meetupsHosted, meetupsJoined, completed, noShows, updatedAt }
```

**Indexes**
```js
live_posts.createIndex({ publicLocation: "2dsphere" })
live_posts.createIndex({ location: "2dsphere" })
live_posts.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 })
live_posts.createIndex({ authorMemberId: 1, status: 1 })
live_tracks.createIndex({ at: 1 }, { expireAfterSeconds: 86400 })
live_tracks.createIndex({ postId: 1, memberId: 1, at: -1 })
```

### 3.2 Android (Room — `ScreensaverDatabase` v26)

```kotlin
@Entity(tableName = "live_posts")
data class LivePostEntity(
    @PrimaryKey val postId: String,
    val authorMemberId: String, val authorName: String, val authorAvatarUrl: String?,
    val text: String, val category: String, val mood: String?,
    val lat: Double, val lng: Double, val placeLabel: String?,
    val radiusM: Int, val distanceM: Int,      // distance from me, server-computed
    val visibility: String, val status: String,
    val maxJoiners: Int?, val acceptedCount: Int, val isMine: Boolean,
    val meetupGroupCode: String?,
    val startsAt: Long, val expiresAt: Long, val cachedAt: Long
)

@Entity(tableName = "live_participants")   // who's in an active meet-up + last position
data class LiveParticipantEntity(
    @PrimaryKey val id: String,            // "$postId:$memberId"
    val postId: String, val memberId: String, val name: String, val avatarUrl: String?,
    val status: String,                    // PENDING | ACCEPTED | LEFT
    val lat: Double?, val lng: Double?, val heading: Float?, val etaMin: Int?,
    val updatedAt: Long
)
```
Migration `MIGRATION_25_26` creates both tables (no destructive change).

---

## 4. API contract

### 4.1 REST (`gigi-server/server.js`)

| Method | Path | Body / Query | Returns |
|---|---|---|---|
| POST | `/api/live/posts` | text, category, mood, lat, lng, radiusM, visibility, maxJoiners, startsAt, durationMin | post |
| GET | `/api/live/posts` | `lat`, `lng` | `{ posts: [...] }` inside their own radius |
| GET | `/api/live/posts/:id` | — | post + participants |
| POST | `/api/live/posts/:id/join` | note | join request |
| POST | `/api/live/posts/:id/respond` | memberId, accept | updated participants |
| POST | `/api/live/posts/:id/leave` | — | ok |
| POST | `/api/live/posts/:id/done` | — | ok (status DONE, tracks purged) |
| POST | `/api/live/track` | postId, lat, lng, heading, speed, battery | ok (fan-out over WS) |
| POST | `/api/live/report` | postId/memberId, reason | ok |

**The nearby query** (the only non-obvious piece — radius belongs to the *post*):

```js
const audience = await audienceMemberIdsFor(me);        // me + connections + FoF
const posts = await LivePost.aggregate([
  { $geoNear: {
      near: { type: "Point", coordinates: [lng, lat] },
      distanceField: "distanceM",
      maxDistance: 5000,                                 // hard cap = biggest radius
      spherical: true,
      query: {
        status: { $in: ["OPEN", "ACTIVE"] },
        expiresAt: { $gt: new Date() },
        $or: [
          { visibility: "PUBLIC" },                      // off in v1
          { audienceMemberIds: me.memberId },
          { authorMemberId: me.memberId }
        ]
      }
  }},
  { $match: { $expr: { $lte: ["$distanceM", "$radiusM"] } } },
  { $sort: { startsAt: 1 } },
  { $limit: 100 }
]);
```

Responses **never** include `location` unless the caller is the author or an accepted
participant — feeds get `publicLocation` + `placeLabel` only.

### 4.2 WebSocket message types

| Type | Direction | Payload |
|---|---|---|
| `live_post_new` | server → audience | post summary (someone near you went live) |
| `live_join_request` | server → author | postId, member, note |
| `live_join_accepted` / `live_join_declined` | server → joiner | postId, meetupGroupCode |
| `live_location` | both | postId, memberId, lat, lng, heading, speed |
| `live_participant_left` | server → room | postId, memberId |
| `live_post_done` | server → room + audience | postId |

Fan-out reuses `broadcastToConnection(meetupGroupCode, …)` — a live meet-up **is** a
Gigi group, so the existing room plumbing works unchanged.

---

## 5. Android — files to create

```
ui/live/
  LiveScreen.kt              feed + mini-map, radius chips, category filter, FAB
  LiveComposeSheet.kt        create a post (text, category, radius, when, who, cap)
  LivePostCard.kt            one post: avatar, text, distance, time, join button
  LiveMapScreen.kt           full map: pins, routes, ETA, participant strip, actions
  LiveJoinRequestSheet.kt    author approves / declines
  LivePermissionScreen.kt    location rationale + request (pre-permission screen)
  components/MapSurface.kt   thin wrapper over Google Maps (swap-able)

data/live/
  LiveRepository.kt          REST + Room cache + flows
  LiveApi.kt                 endpoint calls
  LiveDao.kt                 Room queries
  LiveModels.kt              domain models

service/
  LiveLocationService.kt     foreground service; streams position during a meet-up

viewmodel/
  LiveViewModel.kt           feed state, compose state, active meet-up state
```

Bottom nav gains a 5th item: **📍 Live** (existing morphing nav pill in `MainActivity`).

---

## 6. Maps — concrete plan

**Choice for v1: Google Maps Compose** (`com.google.maps.android:maps-compose`).
Best UX, familiar, generous free tier. Wrapped in `MapSurface` so MapLibre/OSM can be
swapped in if billing ever bites.

```kotlin
// build.gradle.kts
implementation("com.google.maps.android:maps-compose:6.+")
implementation("com.google.android.gms:play-services-maps:19.+")
implementation("com.google.android.gms:play-services-location:21.+")
```
Manifest needs `com.google.android.geo.API_KEY` (restrict to package + SHA-256:
`97:7C:57:…:B9`). **Do not commit the key** — inject via `local.properties` →
`manifestPlaceholders`, given the repo's existing credential history.

**Map styling.** A custom night style JSON to match Gigi's galaxy palette (deep indigo
water, muted violet roads) so it doesn't look like a stock Maps screen.

**What's drawn**
- **Your dot** — Twigi/emoji avatar in a lavender ring
- **Each participant** — their avatar pin, heading arrow, name pill
- **Post origin** — a soft pulsing circle (the meeting point)
- **Radius ring** while composing — live-updating as you change 200 m → 5 km
- **Fuzzed pins** in the browse feed — a 150 m circle, never an exact dot
- **Path trail** — last ~10 positions per person, fading (reuses the comet-trail idea
  from the galaxy for visual continuity)
- **Auto-fit bounds** to keep everyone on screen; recenter FAB

---

## 7. Location engine

| Mode | Priority | Interval | Notes |
|---|---|---|---|
| Browsing Live | `BALANCED_POWER` | one-shot on open + manual refresh | coarse is enough |
| Composing | `HIGH_ACCURACY` | one-shot | fine, for an accurate origin |
| **Active meet-up** | `HIGH_ACCURACY` | 8–12 s, `setMinUpdateDistanceMeters(15)` | **foreground service + ongoing notification** |
| Done / expired | — | stopped, service killed | tracks purged server-side |

- **No `ACCESS_BACKGROUND_LOCATION`.** Foreground service with type `location`, which
  keeps us inside Play's rules without a background-location declaration.
- Notification: *"Sharing your location with Aman · tap to stop"* + **Stop** action.
- Battery guard: pause updates when stationary >5 min; resume on significant movement.
- Auto-stop at `expiresAt` even if the user forgets.

---

## 8. Safety (lighter for connections-first, still real)

Kept from the full plan because they're cheap and matter even among friends:
1. **Fuzzed location until accepted** (~150 m grid + jitter).
2. **Author approves every joiner** — no silent joins.
3. **Time-boxed**: posts expire, tracking auto-stops, tracks TTL-delete in 24 h.
4. **Guardian share** — one tap to loop in another Gigi connection.
5. **Report / block** on posts and people → admin moderation queue.
6. **Stop sharing** always one tap away (notification, map screen, tile).
7. **Trust score** — completed meet-ups, shown on the post card.
8. **Rate limits** — max 3 open posts; join cooldown after a report.
9. **Home-radius warning** when posting from a frequent location.

Deferred until `PUBLIC` is switched on: 18+ gate, women-only/verified-only filters,
panic button, heavier moderation, content rating change.

---

## 9. Feature list (the rich version)

**Composing**
- Category chips with emoji · free-text (180 chars) · mood emoji
- **When**: now · in 30 min · in 1 h · pick a time
- **Duration**: 1 h / 2 h / 4 h (drives `expiresAt`)
- **Radius picker** with live ring on the mini-map
- **Who**: my connections · friends-of-friends
- **Cap**: 1 / 3 / 5 / unlimited
- Templates ("coffee?", "walk?", "study session")

**Feed**
- Cards sorted by start time, with **distance** ("320 m away") and countdown
- Filter by category / radius / "starting soon"
- Mini-map toggle — feed ⇄ map
- **Mutual connections badge** ("2 friends in common")
- Trust badge (meet-ups completed)
- Empty state → "Nobody's live nearby. Be the first ✨"

**Meet-up (live)**
- Shared map with everyone's avatars + heading + fading trails
- **ETA + distance** per participant
- **"I'm here"** ping → everyone gets a nudge
- **Auto-created Gigi group**: chat, doodle, sparkle, break cards — *the differentiator*
- Guardian share · Stop sharing · Leave
- Author: approve/decline, **Done**

**After**
- **"We met ✅"** confirmation from both → trust score
- Optional shared album of sparkles from the meet-up
- Auto-archive the group after 24 h (or keep it, user's choice)

**Nice extras**
- Recurring posts ("walk every evening 7 pm")
- Ghost mode (browse without appearing)
- Campus/office mode (bounded venue)
- Live activity heat-map
- Widget: "2 people live near you"

---

## 10. Phases & deliverables

| Phase | Deliverable | Size |
|---|---|---|
| **0** | Prereqs: commit, **key rotation (#19)**, Maps API key, privacy policy + data-safety update, Room v26 | 0.5 d |
| **1** | Server: schemas, indexes, all REST endpoints, audience resolution, fuzzing | 1.5 d |
| **2** | Android: Live tab, permission screen, feed (cards + distance), compose sheet with radius ring | 2 d |
| **3** | Join flow: request → approve → auto-create Gigi group + chat | 1 d |
| **4** | **Live map**: MapSurface, pins, trails, ETA, `LiveLocationService`, WS streaming, Done | 3 d |
| **5** | Safety: report/block, admin queue, guardian share, trust score, rate limits | 1.5 d |
| **6** | Delight: "we met", recurring, filters, heat-map, widget | open |

**~9–10 working days to a complete, safe, shippable Live (Phases 0–5).**

---

## 11. Testing plan

- **Two physical devices** — the emulator can fake GPS (`adb emu geo fix lng lat`),
  so emulator + phone is a workable pair for the map.
- Scripted GPS walk on the emulator to verify trails, ETA, and auto-fit.
- Radius correctness: post at 200 m with a viewer at 150 m (visible) and 250 m (hidden).
- Kill-the-app test: tracking must stop and the notification must clear.
- Airplane-mode test: queue positions, resume on reconnect, no crash.
- Expiry test: post auto-expires, service stops, tracks TTL-delete.
- Battery: 1 h active meet-up, measure drain; target <6 %/h.

---

## 12. Open decisions for you

1. **Friends-of-friends included in v1, or connections only?** (FoF is the growth lever;
   connections-only is the safest start.)
2. **Google Maps vs MapLibre** — Maps needs a billing account today.
3. **Does the meet-up group persist** after Done, or auto-archive?
4. **Scheduling in v1**, or "right now" only?
5. Should Live respect the existing **plan tiers** (e.g. 5 km radius = Plus)?

---

## Build log — what actually shipped (2026-08-09)

Phases 1–3 are built and verified on a device. Decisions taken along the way, which
differ from or resolve the open questions in §12:

- **Audience: connections + optional friends-of-friends.** `PUBLIC` exists in the
  schema but the API rejects it, so the stranger-facing surface (and the safety stack
  it would require) stays unbuilt until it is deliberately switched on.
- **Maps: OpenStreetMap raster tiles, not Google Maps**, for the radius dial
  (`OsmTiles.kt`). No API key, no billing, no Play services dependency for the part
  users touch first. Attribution is drawn on the disc, and a User-Agent identifies the
  app as OSM's tile policy requires. The meet-up tracking map still uses the Google
  Maps SDK and shows a "needs a key" panel until `GIGI_MAPS_API_KEY` is set.
- **Radius and duration are continuous**, not the fixed 200/500/1k/5k list in §4.1:
  200 m – 10 km and 5 min – 5 hr, mapped logarithmically onto a 270° dial so the fine
  end gets the travel it deserves. The server clamps to those ranges.
- **Foreground-only tracking**, as planned. `ACCESS_BACKGROUND_LOCATION` is not
  requested; the service is `foregroundServiceType="location"` with an ongoing
  notification and stops on Done, expiry, or leaving the map screen.
- **Nav order**: Live is the second pill (Reminders, Live, Sweet Corner, Music).

Still open, in rough priority order:

1. **Task #19 — rotate the leaked Firebase key and tunnel token.** Phase 0 of this
   plan. Live puts real-time locations of real people on that backend.
2. **Phase 4 safety**: report/block, moderation queue, trust signals, guardian share,
   panic action, rate limits beyond the 3-open-posts cap.
3. **Privacy policy + Play data-safety answers** do not yet mention location.
4. Auto group chat for accepted joiners (§6's "killer integration" — not built).
5. `GIGI_MAPS_API_KEY` for the meet-up map, or port that screen to OSM tiles too.
