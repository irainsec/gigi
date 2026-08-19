# Nebula — public profiles & discovery

Turning Sweet Corner into two places: your **galaxy** (the people you chose) and, drifting
below it, the **nebula** (everyone who wants to be found).

---

## 0. The thing to get right first

Gigi is not a social app that happens to have location. It is a *couples* app with **live
location tracking**, presence, now-playing and shared photos. Discovery bolted onto that
without care means a stranger taps "connect", you accept while half-asleep, and they can
now watch you walk across a map in real time.

So one rule governs everything below:

> **A nebula connection is not the same kind of connection as an invited partner.**

The app already has the perfect metaphor for this — the orbit rings:

```
My Heart  ->  Close Ones  ->  Dear Stars  ->  Faraway
    ^                                            ^
 full trust                        where nebula people land
```

`ORBIT_NAMES` in `GalaxyView.kt:89` is already this ladder. Nebula connections arrive in
**Faraway** and can only move inward by a deliberate act. Trust becomes something you earn
by being dragged closer — which is the safety model and a lovely interaction at once.

Concretely:

- `Connection` gains `origin: INVITE | NEBULA` and `trustRing: Int`.
- Live's `audienceMemberIds` (see `live_routes.js`) **excludes NEBULA-origin connections**
  unless promoted past Faraway. Location is opt-in, per person, never automatic.
- Same for precise presence and any future location surface.

Skip this and the safest version of the feature is the one we ship by accident. Far better
to build the ladder now, with two users, than retrofit it later with two thousand.

---

## 1. Data model

### Server — `members`

```js
discoverable:      Boolean   // the public/private switch. default FALSE
handle:            String    // unique, lowercase [a-z0-9_], 3-20. the searchable name
bio:               String    // 80 chars, optional, sanitised
discoverableSince: Date
nebulaSeed:        Number    // stable per member; drives their drift path, so a person
                             // sits in roughly the same part of the cloud each visit
```

Indexes: `{ handle: 1 }` unique sparse, `{ discoverable: 1, discoverableSince: -1 }`.

`displayName` and the avatar fields already exist.

### Server — new collections

```js
nebulaInvites  { fromMemberId, toMemberId, status: PENDING|ACCEPTED|DECLINED,
                 createdAt, respondedAt }        // TTL 14d on PENDING
memberBlocks   { memberId, blockedMemberId, createdAt }
memberReports  { reporterId, reportedId, reason, note, createdAt }
```

### App — `Connection`

```kotlin
val origin: String = "INVITE"    // INVITE | NEBULA
val trustRing: Int = 0           // 0 My Heart .. 3 Faraway
```

One `ScreensaverDatabase` version bump; both columns default safely, so existing rows are
untouched.

**What a public profile exposes — the entire list, deliberately short:** handle,
displayName, avatar (emoji or Twigi URL), bio, and a coarse "active recently" flag.
**Never** email, phone, deviceId, memberId, location, connection count, or who someone is
connected to.

---

## 2. Server endpoints

```
POST /api/profile/discoverability   { discoverable, handle?, bio? }
GET  /api/nebula/browse?cursor=     -> sampled page of public profiles
GET  /api/nebula/search?q=          -> handle prefix + displayName match
POST /api/nebula/invite             { toHandle }
POST /api/nebula/invite/respond     { inviteId, accept }
POST /api/nebula/block              { handle }
POST /api/nebula/report             { handle, reason, note? }
```

**Browse must not return everyone.** At any real size the client cannot render thousands of
motes and should not receive thousands of profiles. Return a stable sampled page of ~60,
seeded per viewer per day so the cloud feels like a *place* rather than a reshuffled list —
either `$sample` or a `nebulaSeed` range scan.

Every browse and search result is filtered server-side to drop: yourself, anyone already
connected, anyone with a pending invite in either direction, anyone you blocked, and anyone
who blocked you.

**Rate limits** (reuse the limiter from task #2):

| Action | Limit |
|---|---|
| invites | 10/day, 3/hour |
| search | 30/min |
| discoverability flips | 5/day |

That last one stops people farming visibility by toggling in and out of the sample.

**Handle claiming** is the fiddly part: unique index, case-folded, reserved words blocked,
and a clear "that one is taken" state. Handles are also the only way to find a *specific*
person, so they carry more weight than they appear to.

---

## 3. The nebula itself

### Scene layout

One camera, two regions. `GalaxyCamera` already carries `panY`, so the nebula lives **below**
the galaxy in the same coordinate space — roughly 1.6 screen-heights down. Panning past the
outermost orbit ring drifts you into it: no navigation, no screen change, you *travel* there.
Between them sits a quiet gap of sparse dust so the transition reads as distance.

`GalaxyView.kt` already has a private `Nebula(ox, oy, radius, color)` used for background
decoration. The real one is that idea at fifty times the size and detail.

### Making it look like an actual nebula

Layered back to front, all in the existing Canvas:

1. **Gas clouds** — 5-7 large overlapping radial gradients in magenta/cyan/violet, each
   drifting on its own slow sine. Overlapped at ~0.15 alpha with `BlendMode.Plus` so
   intersections bloom brighter. That blooming is what sells "gas" instead of "blurry
   circles".
2. **Filament wisps** — a few long bezier ribbons, soft wide stroke plus a bright thin core,
   control points easing slowly.
3. **Dust** — 200-300 points across three parallax depths. The far layer barely moves when
   you pan, the near layer moves most. Parallax is what creates depth.
4. **Twinkle** — a handful of points pulsing alpha on staggered phases.
5. **Motes (the people)** — avatar or Twigi inside a soft glow bubble, drifting a slow
   lissajous seeded from `nebulaSeed`, with a gentle bob. Recently-active people glow a
   little warmer.

Every layer reads camera state **inside the draw lambda**, never in composition — the same
discipline that fixed the Live map's gesture lag. This is the biggest perf risk in the
feature and the mistake is already documented in this codebase.

### Recenter control

The single **Recenter** pill becomes a two-segment morphing toggle when public:

```
[ 🏠 My Galaxy | 🌌 Nebula ]
```

Tapping animates the camera between regions (~700ms, `FastOutSlowInEasing`, slight
overshoot). The highlight slides; the destination icon pulses once. When private it stays
exactly the pill it is today.

### Search

A field appears once you are in the nebula region. As you type:

- matching motes **brighten, scale up and drift toward the centre**
- non-matching motes **dim to ~0.15 and drift outward**
- the gas darkens slightly so matches pop

Clearing lets everything ease back. It is the cute version of a filtered list, and costs
only a target position per mote.

### Connecting

Two routes, one outcome:

- **Tap a mote** → profile bubble (avatar, handle, name, bio, "Invite to my galaxy", plus
  an overflow with Block / Report) → invite sends, the mote gains a "sent 💌" ring and
  leaves the browse pool.
- **Drag a mote upward** past the gap into your galaxy → it flies an arc to the Faraway
  ring, lands with a soft bounce and a ripple, and the invite sends. Declined or expired,
  it quietly drifts back down.

While dragging, camera pan is **disabled** — one gesture owner at a time. The drop needs an
affordance: the Faraway ring brightens as you cross the threshold. Drag is the delightful
route and also the fiddly one, which is why it lands after tap.

Until accepted, the planet is a **pending ghost** — translucent, dashed orbit, "waiting to
be accepted". Note this collides with the placeholder filter at
`ScreensaverViewModel.kt:588`, which currently hides unjoined connections entirely. Pending
nebula invites must render as visibly pending, or people will think the invite vanished —
exactly the confusion that made the galaxy look empty earlier today.

---

## 4. Phases

**Phase 1 — the switch.** Server `discoverable` + `handle` + endpoint. App: a
Public/Private toggle in settings with a handle field and plain-English text on exactly what
becomes visible. Nothing else changes. Safe and shippable alone.

**Phase 2 — the trust ladder.** `origin` + `trustRing`, Room migration, Live audience
exclusion. Do this **before** anyone is discoverable, so the safety model exists ahead of
the risk.

**Phase 3 — the nebula, look only.** Scene, gas, dust, parallax, motes from `browse`, the
two-segment recenter, camera travel. No connecting. The big visual piece.

**Phase 4 — search & tap-to-invite.** Search endpoint plus the brighten/dim choreography,
profile bubble, invite/accept/decline end to end, pending ghosts, notifications both sides.

**Phase 5 — drag-to-connect & polish.** The drag arc, threshold affordance, landing bounce,
haptics, empty states ("the nebula is quiet tonight"), block/report surfaced properly.

---

## 5. Things that will bite

- **Rendering cost.** The galaxy Canvas is already busy. Gas, dust and motes on top can drop
  frames. Cache the static gas layers into a bitmap that only redraws on zoom change, cap
  motes near 60, and never read camera in composition.
- **An empty nebula.** With two users it is a beautiful empty room. It needs a real empty
  state, and socially the feature does not start working until a few hundred people.
- **Avatar loading.** Sixty remote Twigi images at once. Coil with a bounded memory cache
  and a placeholder mote, or drifting in will stutter.
- **Abuse.** Invite spam, handle squatting, impersonation ("Aman Raj" ×20). Rate limits
  help; a report queue in the admin panel is the actual answer.
- **Age.** A stranger-discovery surface attached to a couples app has an obvious
  minor-safety dimension. At minimum gate discoverability behind a self-declared 18+ with a
  clear notice. Worth real thought before Phase 4.
- **Privacy policy.** Public profiles are a new category of processing and must be described
  before launch, not after.

---

## 6. Open questions

1. **Is nebula a paid feature?** It fits the plan-tier system. Note the Spotify-style
   "cannot monetize" restriction does not apply — this is our own surface, so gating is
   entirely ours to choose.
2. **Handle or name search?** Handles are unambiguous but need claiming; names are
   friendlier but collide. Proposal: claim a handle when going public, search matches both.
3. **Can you browse while private?** Proposal: **no**. Being visible is the price of
   looking. It is fair, and it grows the population.
4. **What happens when you go private again?** Proposal: you vanish from browse
   immediately, existing connections are untouched, invites you already sent stay live.
