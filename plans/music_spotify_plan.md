# Music: replacing YouTube Music — honest options (2026-07)

## TL;DR

**Removing the YouTube Music downloader: YES, do it.** It's a real legal liability.

**Replacing it with "connect Spotify, sync library, play inside Gigi": NOT POSSIBLE.**
Not "hard" — actually blocked, on two independent grounds. Details below, then three
plans that *do* work.

---

## 1. Why the YouTube downloader must go (agree with the instinct)

Downloading/extracting audio from YouTube violates the YouTube Terms of Service. For a
Play Store app this is a takedown risk and a legal risk, independent of anything else.
Removing it is a straight win. Files involved (audit before deleting):

- `data/music/YoutubeMusicClient.kt`, `data/music/MusicDownloader.kt`
- search UI in `ui/Music.kt` ("Search YouTube Music…")
- keep: `utils/MusicLibraryScanner.kt`, `service/MusicPlaybackService.kt`,
  `viewmodel/MusicViewModel.kt` / `PlaybackViewModel.kt`, local + shared album stores

---

## 2. Why "connect Spotify and play in Gigi" cannot be built

### Blocker A — Spotify forbids playing their audio in your player
There is no legal path to stream Spotify audio through Gigi's own player. The only
sanctioned playback routes are:
- **Android App Remote SDK** — remote-controls the *installed Spotify app*; audio comes
  out of Spotify, not Gigi. Requires Spotify installed + **Premium**.
- **Web Playback SDK** — browser only, Premium only. Not usable in native Android.
- 30-second `preview_url`s have been progressively removed.

So "play directly from here only" is off the table by design.

### Blocker B — the quota catch-22 (this is the fatal one)
As of the 2025/2026 policy changes:
- A new app sits in **Development Mode: capped at ~5 users**, each of whom must be
  manually allow-listed *and* hold Spotify Premium.
- **Extended Quota Mode** (the thing that lifts the cap) now requires: a legally
  registered business, an **active launched service with ~250,000 monthly active
  users**, availability in key Spotify markets, and an application from a company email
  (individuals are no longer accepted). Review takes up to ~6 weeks.

You need 250k MAU to earn the quota that would let you have more than 5 users.
For Gigi, Spotify integration is a **hard no** at any realistic scale.

Sources:
- https://community.spotify.com/t5/Spotify-for-Developers/Updating-the-Criteria-for-Web-API-Extended-Access/td-p/6920661
- https://techcrunch.com/2026/02/06/spotify-changes-developer-mode-api-to-require-premium-accounts-limits-test-users/
- https://developer.spotify.com/documentation/web-api/concepts/rate-limits

*(Apple MusicKit and SoundCloud have comparable gatekeeping; Deezer/Napster APIs are
effectively closed to new consumer apps. This isn't a Spotify-specific wall.)*

---

## 3. What DOES work — three plans

### ⭐ Plan A — "Now Playing" sharing (recommended)
Gets ~90% of the couples value with **zero** third-party API, zero quota, zero ToS risk.

- Read the phone's current media session via `MediaSessionManager` /
  `NotificationListenerService` (Gigi already holds notification access). This reports
  track + artist + album art for **any** player — Spotify, YouTube Music, Apple Music,
  local.
- Broadcast "Aman is listening to *Teri Yaad*" to connections — reuse the existing
  presence/WS fanout; show it on the partner's galaxy planet + Sweet Corner card.
- Partner taps → deep-link **into their own Spotify/YT Music app** to play it:
  `https://open.spotify.com/search/<track artist>` (or `spotify:search:` URI). No API,
  no auth, works for Free users, no install of ours required.
- Optional cute layer: "listening together 💞" state when both are on the same track;
  a heart pulse in the galaxy.

**Cost:** small. **Risk:** none. **Works for:** everyone, any music app.

### Plan B — Local library only (simplest, ship-ready)
Keep the on-device music scanner + player you already have; delete the YouTube search.
Users play their own files; shared albums/playlists keep working between partners.
No streaming service involved at all.

### Plan C — Spotify "connect" as a 5-user demo (not for launch)
If you personally want it for you + partner only: register a Spotify app, allow-list
your accounts (both need Premium), use App Remote to control the Spotify app from Gigi
and Web API to list playlists. **Never shippable publicly** — hard-capped at ~5 users.
Only worth it as a private toy.

---

## 4. Recommendation

**Do B + A:** rip out the YouTube downloader/search (legal cleanup), keep local
playback, and build **Now Playing sharing** — which is far more "Gigi" than a music
player anyway: it's about *feeling close*, not about being a Spotify client.

If you want the Spotify *look* (their library in-app), that specific thing cannot ship.
But "see what your person is playing right now, tap to join them" can — and it works
with Spotify, without needing Spotify's permission.
