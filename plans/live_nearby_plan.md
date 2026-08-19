# "Live" — nearby posts, joining, and live meet-up tracking

> Implementation plan + honest risk assessment.
> Status: **plan only, nothing built.** Written 2026-07-27.

---

## 0. Read this first — this is a different product

Everything Gigi does today is **private and consensual**: you connect to people you
already love, by exchanging a code. There are no strangers anywhere in the app.

"Live" as described broadcasts **"here is what I'm doing, here is roughly where I am,
come join me"** to **strangers**, then shares **live location** with them. That is a
different category of app, with a different risk profile, different Play Store rules,
different legal duties, and different failure modes. Concretely:

- A woman posting "coffee at 8pm, 200m radius" is telling nearby strangers where she is.
- Once someone joins, they get her **live position, continuously**.
- If something goes wrong, it goes wrong *physically*, not digitally.

This is buildable and can be built responsibly — but the safety design is **not a
later phase**. It's the feature. Apps that shipped this casually (several
location-based "meet nearby" apps) generated real-world harm and either added heavy
verification or died. Nextdoor's answer was **address verification for everyone**;
Meetup's answer was **public groups with hosts and no live location**.

**My strong recommendation is §7: ship "Live" to your social graph first**, not to
strangers. Same feature, most of the value, a fraction of the risk. Read that section
before committing to the open-to-strangers version.

Sources consulted:
- https://support.google.com/googleplay/android-developer/answer/9799150 (background location policy)
- https://support.google.com/googleplay/android-developer/answer/16926792 (April 2026 policy update)
- https://support.google.com/googleplay/android-developer/answer/17033915 (minimum scope / location button)
- https://developer.android.com/develop/sensors-and-location/location/background

---

## 1. What the user asked for

1. New bottom-nav tab: **Live**
2. Ask for location permission
3. User writes a cute post: *what I'm doing + open to company*
4. Author picks a **radius**: 200 m / 500 m / 1 km / 5 km
5. Post is visible to users **inside that radius**
6. Others tap **"I want to join"**
7. On accept → **both live locations shared**, shown on a **Google-Maps-style map**
8. Works for **groups** (multiple joiners)
9. Author marks **Done** → post disappears from Live

All of that is in the plan below, plus the safety layer it needs to be shippable.

---

## 2. Compliance & policy constraints (these shape the design)

| Constraint | Consequence for us |
|---|---|
| **Play: background location needs a declaration + review**; "nice to have" is rejected, and unapproved apps get **blocked or removed** | **Design for foreground-only.** Live tracking runs while the meet-up is active with a **visible foreground-service notification**. Do not request `ACCESS_BACKGROUND_LOCATION` in v1. |
| **Play (Apr 2026): minimum scope, prefer the location button / one-shot access** | Post creation uses **one-shot** location. Continuous updates only during an accepted meet-up. |
| **Precise vs approximate** | Request `ACCESS_COARSE_LOCATION` for browsing Live; `ACCESS_FINE_LOCATION` only when a meet-up starts. |
| **India DPDP Act / GDPR** — location is sensitive personal data | Need consent copy, retention limits (auto-delete tracks), export/delete support. **Privacy policy must be updated before launch** — the current one does not cover location sharing with third parties. |
| **Play: user-generated content policy** | Public posts require **report, block, and moderation** — mandatory, not optional. |
| **Play: apps facilitating meeting strangers** | Expect stricter review; content rating changes (likely Mature 17+ if open to strangers). |

---

## 3. Safety architecture (non-negotiable for the public version)

Designed-in, not bolted-on:

1. **Fuzzed post location.** The post stores exact coordinates server-side but is
   **published snapped to a ~150 m grid with jitter**. Browsers see a *neighbourhood*,
   never a doorstep. Exact position is revealed **only after mutual accept**.
2. **Mutual consent before any live location.** Joiner requests → **author approves**.
   No approval, no location, no chat. (The user's spec has joiners auto-joining; this
   one change removes most of the risk.)
3. **Time-boxed everything.** Posts auto-expire (default 2 h, max 8 h). Tracking stops
   at Done / expiry / app close. Location history auto-deletes after 24 h.
4. **Never post from home.** Warn when the post origin is within ~200 m of a
   frequently-used location, and suggest a public place.
5. **Public-place nudge.** Suggest cafés/parks as the meeting point (Places API or a
   curated list).
6. **Guardian share.** One tap to share a live meet-up with an existing **Gigi
   connection** (someone you actually trust) — this is where Gigi's existing graph is
   a genuine advantage over other apps.
7. **Report / block / mute**, reachable from every post and profile, with a real
   moderation queue in the admin console.
8. **Trust signals**: account age, completed meet-ups, verified phone, mutual
   connections ("2 friends in common"). Show them on every post.
9. **Rate limits**: max 3 open posts, max N joins/day, cooldown after a report.
10. **Women-only / verified-only post options** — important for the India-first market.
11. **Panic action** during an active meet-up: end + notify guardian + surface helplines.
12. **18+ gate** for the stranger-facing surface.

---

## 4. Technical design

### 4.1 Data model (MongoDB — already in the stack)

```js
// live_posts
{
  postId, authorMemberId, authorName, authorAvatarUrl,
  text,                       // "chai + rant, anyone? ☕"
  category,                   // coffee | walk | study | food | sport | help | other
  location: { type: "Point", coordinates: [lng, lat] },   // 2dsphere index
  publicLocation: { type: "Point", coordinates: [lng, lat] }, // fuzzed, what others see
  radiusM,                    // 200 | 500 | 1000 | 5000
  maxJoiners,                 // null = unlimited
  visibility,                 // PUBLIC | CONNECTIONS | FRIENDS_OF_FRIENDS
  womenOnly, verifiedOnly,    // booleans
  status,                     // OPEN | FULL | ACTIVE | DONE | CANCELLED | EXPIRED
  startsAt, expiresAt, createdAt, doneAt
}

// live_join_requests   (postId, memberId, status: PENDING|ACCEPTED|DECLINED, at)
// live_tracks          (postId, memberId, loc, at)  ← TTL index, 24h
```

**Indexes:** `2dsphere` on `location` and `publicLocation`; TTL on `live_tracks.at`
and `live_posts.expiresAt`.

### 4.2 The radius query (the interesting bit)

The radius belongs to the **post**, not the viewer — so "posts whose own radius
contains me". One aggregation does it:

```js
db.live_posts.aggregate([
  { $geoNear: {
      near: { type: "Point", coordinates: [lng, lat] },
      distanceField: "distanceM",
      maxDistance: 5000,          // hard cap = largest allowed radius
      spherical: true,
      query: { status: "OPEN", expiresAt: { $gt: new Date() } }
  }},
  { $match: { $expr: { $lte: ["$distanceM", "$radiusM"] } } },   // inside ITS radius
  { $limit: 100 }
])
```

Fast, index-backed, and correct. Distance is also returned for the "320 m away" label.

### 4.3 Endpoints (REST) + WS events

```
POST   /api/live/posts              create (one-shot location)
GET    /api/live/posts?lat=&lng=    nearby feed (aggregation above)
POST   /api/live/posts/:id/join     request to join
POST   /api/live/posts/:id/respond  author accepts / declines
POST   /api/live/posts/:id/done     close it
POST   /api/live/track              push my position (accepted meet-ups only)
```

WS message types (reuse the existing socket + `broadcastToConnection` pattern):
`live_join_request`, `live_join_accepted`, `live_location`, `live_post_done`.
A meet-up gets an **ephemeral room** so tracking fans out to exactly its participants.

### 4.4 Map

| Option | Cost | Verdict |
|---|---|---|
| **Google Maps SDK** | Free tier generous; billing account required | **Recommended for v1** — best UX, familiar, fastest to build |
| **MapLibre + OSM tiles** | Free / self-host | Good fallback if map bills grow |
| **osmdroid** | Free | Dated UX |

Keep it behind a thin `MapSurface` interface so swapping later is cheap.

### 4.5 Location plumbing

- `FusedLocationProviderClient`, `PRIORITY_BALANCED_POWER_ACCURACY` while browsing.
- During an active meet-up: `PRIORITY_HIGH_ACCURACY`, **~10 s** interval, inside a
  **foreground service with an ongoing notification** ("Sharing your location with
  Aman — tap to stop"). Stops on Done/expiry.
- Battery: no updates when the tab isn't visible and no meet-up is active.

---

## 5. Build phases

**Phase 1 — Feed (no location sharing).** Live tab, permission flow, create post
(one-shot location), radius picker, nearby feed with distance + fuzzed pin, Done.
*Ship-able alone; zero live-tracking risk.*

**Phase 2 — Joining.** Request → author accepts/declines → auto-created group chat
(reuse the existing group + chat system, which already works). Still no live location.

**Phase 3 — Live map.** Map screen, participant pins, live position streaming for
accepted participants only, foreground service + notification, ETA, "I'm here",
auto-stop. *This is the phase that needs the Play declaration and privacy-policy update.*

**Phase 4 — Safety & trust.** Report/block, moderation queue in admin, trust signals,
guardian share, panic, rate limits, women-only/verified-only.
**Phase 4 must ship with (or before) Phase 3 publicly — not after.**

**Phase 5 — Delight.** Categories/vibes, time windows ("now" / "in 30 min" / "tonight"),
photos, recurring posts, activity heat-map, post-meet "we met ✅" confirmations that
build a trust score.

---

## 6. Extra ideas worth having (asked for)

- **Vibes not just text** — pick a category emoji; the feed becomes scannable.
- **"Starting in 30 min"** — scheduling beats "right now only".
- **Auto group chat + auto Sweet Corner group** for joiners — you already have groups,
  chat, doodles, breaks. A meet-up becomes a temporary Gigi group. **This is the
  killer integration**: no other app can hand you a shared doodle canvas on arrival.
- **"We met ✅"** — both confirm; feeds a visible **trust score**. This is the single
  best long-term safety mechanism.
- **Mutual connections badge** — "2 people you know" instantly changes trust.
- **Campus / office mode** — restrict to a verified domain or venue. Much safer, and
  a great wedge to launch in one college or office.
- **Recurring** — "walk every evening 7pm".
- **Ghost mode** — browse without appearing.
- **Guardian share** (safety + genuinely on-brand for Gigi).

---

## 7. ⭐ The version I'd actually build first

**"Live" limited to your connections and their connections (2nd degree).**

- Same tab, same posts, same radius, same map, same live tracking.
- Visible only to people you're connected to, or friends-of-friends.
- **Removes nearly all the stranger-danger surface**, so Phase 4 becomes lighter.
- Keeps Gigi's soul: *the app about your people*, now with "who's free near me?"
- **Much easier Play review**, no 17+ rating, simpler privacy story.
- Then, if it's loved, open a **PUBLIC** visibility option later with the full safety
  stack behind it — the data model above already has the `visibility` field for that.

Doing it this way also means Phases 1–3 can ship in roughly the time Phase 4 alone
would take for the public version.

---

## 8. Honest cost estimate

| Phase | Rough size |
|---|---|
| 1 — Feed | 2–3 days |
| 2 — Joining + group chat | 1–2 days |
| 3 — Live map + tracking | 3–4 days (map, FGS, streaming, testing) |
| 4 — Safety & moderation | 3–5 days (public version); ~1 day (connections-only) |
| 5 — Delight | open-ended |

Plus: Play declaration + review (**up to weeks**), privacy-policy rewrite, and Maps
billing setup.

---

## 9. Prerequisites before writing any Live code

1. **Commit the current work** — a lot is uncommitted and this is a large new surface.
2. **Task #19 — rotate the leaked Firebase key + tunnel token.** This feature adds
   *real-time location of real people* to that same backend. Shipping location tracking
   on infrastructure with exposed credentials in git history is not a risk worth taking.
3. Decide **§7 connections-first vs public** — everything else follows from it.
4. Update the privacy policy + Play data-safety answers for location.
