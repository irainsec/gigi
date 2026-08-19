# Widgets, history & shareable songs — implementation plan

> Inspired by MD Vinyl's widget suite and share cards, rebuilt in Gigi's voice.
> Written 2026-08-15. Status: **plan only, nothing built.**

---

## 0. Why these four, in this order

The reference app does four things we don't: home-screen widgets, a Now Playing
widget, recently-played history, and pretty share cards. They're listed here in the
order I'd build them, which is **cheapest-and-most-loved first**, not the order they
appear in the screenshots.

Gigi already has two ingredients nobody else has: **your people**, and a galaxy that
already shows what they're listening to. So the share card shouldn't just be a pretty
picture of a song — it should be a thing you send *to someone*. That's the wedge.

---

## 1. Recently played (½ day) — start here

The smallest piece, and everything else leans on it.

**Data.** New Room table `recent_plays(songId, title, artist, albumArtUri, playedAt)`.
Write one row from `MusicViewModel.playSong()`. Keep the last 100; dedupe by songId so
replaying a track moves it up rather than filling the list.

**UI.** A third deck in the Library pager — *Albums · All songs · Recent* — reusing the
sleeve stack that's already there. Newest sleeve on top.

**Why first:** it's the data source for the widget's "recently played" row, and it's
useful on its own the day it ships.

---

## 2. Now Playing widget (1½ days)

Glance is already a dependency (`androidx.glance:glance-appwidget`) and unused — this
is the cheapest visible win in the app.

**Small (2×2):** rotating vinyl of the current album art, title, play/pause.
**Medium (4×2):** art, title, artist, previous / play-pause / next.
**Large (4×4):** the above plus a row of four recent sleeves that play on tap.

**Mechanics**
- `GlanceAppWidget` + `GlanceAppWidgetReceiver`, state via `GlanceStateDefinition`.
- Update on every playback state change — push from the media service rather than
  polling, or the widget lies about what's playing.
- Actions go through `actionRunCallback` → the existing media session, *not* a new
  playback path. Two sources of truth for "is it playing" is how widgets end up
  showing pause while the music plays.
- Album art must be a resized bitmap: Glance has a hard IPC size limit and full-size
  artwork silently blanks the widget.

**Cute, and ours:** the vinyl actually *spins* while playing and eases to a stop when
paused. One detail, done properly, is worth five half-done ones.

---

## 3. Share a song (1½ days) — the one that spreads

MD Vinyl exports a pretty card. We can do that *and* send it to someone who already
matters, which is the whole point of Gigi.

**The card.** Rendered in Compose, captured off-screen to a bitmap:
- Album art as a vinyl with the sleeve behind it
- Title, artist, a soft gradient pulled from the artwork (Palette API — already used
  for `dynamicPalettes`)
- A tiny "on Gigi" mark, deliberately small

**Two buttons, and the second is the interesting one**
1. **Share** — standard `Intent.ACTION_SEND` with the PNG, for Instagram/WhatsApp.
2. **Send to…** — pick a connection and it arrives as a chat card they can tap to
   play, next to the doodles and break invites. No other music app can do this,
   because no other music app knows who your people are.

**Watch out:** share the bitmap through the existing `FileProvider`; a raw `file://`
URI throws `FileUriExposedException` on modern Android.

---

## 4. Widget suite & customisation (2 days) — only if the above lands

Multiple widget styles, colour themes, a compact search widget. This is where the
reference app spends most of its surface area, and it's the least differentiated
work in the list. Ship 1–3 first and see whether anyone asks for it.

---

## Rough cost

| Piece | Size | Depends on |
|---|---|---|
| Recently played | ½ day | — |
| Now Playing widget | 1½ days | recently played (for the large size) |
| Share a song | 1½ days | — |
| Widget suite | 2 days | Now Playing widget |

**~3½ days for the first three**, which is the part I'd actually ship.

---

## The honest caveat

Widgets are a maintenance tax: every playback path has to remember to update them, and
a stale widget reads as a broken app. Worth it for Now Playing, which people genuinely
glance at. Less obviously worth it for a search widget nobody asked for.

If only one gets built, make it **§3 — share to a connection**. It's the one that makes
Gigi more Gigi, rather than making Gigi more like every other music player.
