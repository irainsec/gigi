# 🎵 Gigi App — Full Review & Feature Suggestions

## What This App Is

Gigi is a **couples / close-friends companion app** with four main tabs:

| Tab | Name | Purpose |
|-----|------|---------|
| 0 | **Reminders** | Personal + partner-shared alarms |
| 1 | **Sweet Corner** | Scribble messages, GIF reactions, Love Cards, partner presence |
| 2 | **Moonlight** | Partner connection hub (scribble, screen mirror, file browse, location) |
| 3 | **Music** | Full vinyl-turntable music player with themes, tonearm drag, crackle |

---

## 🐛 Bugs / Things That Need Fixing

### 1. `Music.kt` — Progress ticker fires every 250ms but `progressFraction` triggers recomposition of heavy UI
**Where:** `PlaybackManager.ensureProgressTicker()` — 250ms interval updates `progressMs`, which flows up to `MusicUiState.progressMs`, which causes the entire `MusicPlayerScreen` to recompose (including `HeroTurntable` and `LandscapeToneArm`).  
**Fix:** Separate progress state from UI state, or use `derivedStateOf` to limit recomposition scope.

---

### 2. `PlaybackManager` — `seekTo()` takes `Long` but `MediaPlayer.seekTo()` takes `Int`
**Where:** `PlaybackManager.kt` line `mediaPlayer?.seekTo(safePosition.toInt())`  
For very long files (>35 minutes), `Int.MAX_VALUE` overflows. On API 26+ `MediaPlayer.seekTo(Long, Int)` should be used.  
**Fix:** Use `seekTo(safePosition, SEEK_CLOSEST)` on API ≥ 26, fallback to `toInt()` otherwise.

---

### 3. `Music.kt` — `AudioTrack` constructor marked as deprecated
**Where:** Line ~9414 (seen in build warning). The old `AudioTrack(int, int, int, int, int, int)` constructor is deprecated.  
**Fix:** Replace with `AudioTrack.Builder()` API (the `VinylCrackleEngine` likely uses it).

---

### 4. `Music.kt` — `Icons.Filled.ArrowBack` deprecated
**Where:** Line ~4106.  
**Fix:** Replace with `Icons.AutoMirrored.Filled.ArrowBack` to support RTL layouts properly.

---

### 5. `PlaybackManager` — No audio focus management
**Where:** `PlaybackManager.playSong()` — music plays without requesting audio focus. If a call comes in or another app plays audio, both play simultaneously.  
**Fix:** Request `AudioFocus` via `AudioManager.requestAudioFocus()` before starting playback, and handle focus loss / gain (duck on transient loss, pause on permanent loss).

---

### 6. `MusicPlaybackService` — Notification uses generic `android.R.drawable.ic_media_play` icon
**Where:** `buildForegroundNotification()` line `setSmallIcon(android.R.drawable.ic_media_play)`  
**Fix:** Use a custom app icon drawable for the notification small icon.

---

### 7. `Screensaver.kt` — "Sparkle capture" is a stub
**Where:** Line 362: `Toast.makeText(context, "Captured! (Stub)", ...)`  
The Sparkle camera capture is not implemented — the photo is never actually saved or sent.  
**Fix:** Implement actual photo capture saving and optionally send via the scribble/partner sync channel.

---

### 8. `Reminders.kt` — Debug log left in production code
**Where:** Line 286: `android.util.Log.d("RE_DEBUG", "Reminders updated: ${reminders.size} items")`  
**Fix:** Remove or guard behind a `BuildConfig.DEBUG` flag.

---

### 9. `MainActivity.kt` — Debug log left in production
**Where:** Line 297: `android.util.Log.d("MainActivity", "NavVisibility: ...")`  
**Fix:** Same — remove or guard behind `BuildConfig.DEBUG`.

---

### 10. `Music.kt` — `selectedIndex` can silently drift from `currentSongId`
**Where:** Lines 678–680. When `songs` list changes (e.g. after YouTube download), `selectedIndex` is re-remembered and reset via `mutableIntStateOf(fallbackPage)`, but if the user was mid-swipe, the displayed card may jump.  
**Fix:** Also scroll the `HorizontalPager` to `fallbackPage` via `pagerState.scrollToPage()` inside a `LaunchedEffect(fallbackPage)`.

---

### 11. `PlaybackManager` — `restoreState()` never restores the playback position
**Where:** `restoreState()` only restores `currentSong` and `albumId`, not `progressMs`. App always starts from 0:00.  
**Fix:** Save and restore `progressMs` in SharedPreferences alongside `last_song_id`.

---

### 12. `Music.kt` — Landscape tonearm drag: `armElbow` and related geometry captured in `pointerInput(Unit)` closure at creation time
**Where:** `LandscapeToneArm` — Now that the key is `Unit`, `armElbow`, `pivot`, `lowerArmLength`, `needleOffset`, `minArmAngle`, `maxArmAngle` are all captured once. If the layout is remeasured (e.g. screen rotated again), these stale values would give wrong geometry.  
**Fix:** Wrap these values in `rememberUpdatedState` references or rekey on layout changes with `maxWidth` + `maxHeight`.

---

## ✨ Features to Add

### 🎵 Music Player

#### A. **Repeat & Shuffle Modes** ⭐ High Priority
Currently the queue always plays linearly. There is no way to:
- Repeat one song
- Repeat the whole queue  
- Toggle shuffle on/off without re-seeding the entire queue

Add a row of mode buttons: `🔀 Shuffle`, `🔁 Repeat All`, `🔂 Repeat One`.

---

#### B. **Sleep Timer**
A common feature for bedtime listening. Add a small clock icon on the player that opens a bottom sheet to set a sleep timer (15 / 30 / 45 / 60 min or custom). When it fires, fade out audio gracefully and stop.

---

#### C. **Crossfade Between Tracks**
When a song ends and the next begins, fade the old song out over 2–4 seconds while the new one fades in. This is a premium feel that most streaming apps have.

---

#### D. **Speed Control**
Add a playback speed selector (0.5×, 0.75×, 1×, 1.25×, 1.5×, 2×). Useful for podcasts or audiobooks that people may put in the music folder. `MediaPlayer` supports `setPlaybackParams()`.

---

#### E. **Album Art as Palette Source for "Auto" Theme**
In `AUTO` mode, the app could extract dominant colors from the album art bitmap using `Palette` API and use them to dynamically drive `backgroundTop`, `backgroundBottom`, and `accent` — making the entire player skin adapt to each song's art.

---

#### F. **Equalizer Integration**
Launch the system equalizer (`android.media.audiofx.Equalizer`) with a button in the player settings, or build a simple 5-band EQ slider panel as a bottom sheet. This is a frequently requested feature for audiophiles.

---

#### G. **Share Song with Partner**
A "share now playing" button that sends the current song title + artist to the connected partner via the existing scribble/sync channel — like a "listen to this!" moment. The partner gets a notification with the song name.

---

#### H. **Lyrics Display**
Integrate a lyrics API (LRCLib is free and open-source) to fetch time-synced LRC lyrics. Display them in a scrollable overlay on the vinyl player — the lyrics auto-scroll as the song plays, synchronized with `progressMs`.

---

#### I. **Album Art Screensaver Mode**
When music is playing and the screen sits idle for 30 seconds, transition to a beautiful ambient full-screen album art animation (slow Ken Burns pan + blurred background + song info) — different from the current lock screen. Tap to return.

---

#### J. **Waveform Progress Bar**
Replace the plain Slider progress bar with an animated audio waveform — bars that bounce to the music's amplitude. The `AudioTrack` / `Visualizer` API can provide amplitude data. Would look stunning on the landscape player.

---

### 💌 Sweet Corner / Moonlight

#### K. **Love Card Reaction Sounds**
When a Love Card is flipped/revealed, play a tiny chime or heart sound — makes the card deck feel more alive and joyful.

---

#### L. **Partner "Typing..." Indicator**
Show a small animated "..." bubble when the partner is actively composing a scribble or Love Card. The scribble canvas already syncs in real-time — the presence indicator would make it feel like a live chat.

---

#### M. **Shared Music Queue**
Let partners collaboratively add songs to a shared queue. One person adds a song and the other sees it appear in their "Shared" album. Combined with feature G above, this becomes a full remote listening experience.

---

#### N. **Scribble Canvas: Undo/Redo**
The drawing screen has no undo. A simple `undo()` stack (just keep the last N `Path` objects) would dramatically improve the drawing experience.

---

#### O. **Love Card Animated Stickers / GIF Reactions**
The sticker system is partially built. Extend it so partners can react to each Love Card with an animated GIF chosen from a curated library (the Giphy/Tenor integration already partially exists).

---

#### P. **Reminder — Snooze & "Done Together" Feature**
When a shared alarm fires, both partners get to tap "Done Together" ✓ and both phones play a tiny jingle celebrating the completed task. Creates a accountability + celebration loop.

---

### 🔐 Technical / Infrastructure

#### Q. **Offline Queue for Scribbles**
If the partner is offline when a scribble is sent, it silently fails (you already have a "send failed" dialog). Instead, queue the message locally and retry when connectivity returns — exactly like how SMS drafts work.

---

#### R. **Push Notification for Love Cards**
Currently, a received Love Card deck only appears when the app is open. Add an FCM push notification: "💌 [Partner] sent you a new Love Card deck!" with a deep-link into the Sweet Corner tab.

---

#### S. **Per-Song Theme Memory**
Store the last selected theme preset per song (keyed by `song.id`). When you swipe to a song you previously listened to, the vinyl and theme auto-restore to what you had last time.

---

#### T. **Haptic Rhythm Pulse**
During music playback, use `VibrationEffect.createWaveform()` to pulse the haptic motor on every beat (detected from the amplitude data). This creates a tactile experience for silent environments (e.g., partner sleeping beside you).

---

## 🎨 Creative / Unique Ideas

### U. **"Heartbeat Mode"** — Most Unique Idea
When both partners open the app at the same time and go to the same music player, sync their playback through WebSocket. Both hear the same song at the same timestamp — like listening together even when apart. A small "♥ Listening with [name]" badge appears.

---

### V. **"Mood of the Day" Vinyl Theme**
A daily auto-theme that changes based on the time of day:
- **Morning** → Sakura Spring
- **Afternoon** → Sahara Sunset  
- **Evening** → Stardust Lullaby
- **Night** → Lunar Eclipse

---

### W. **Vinyl Record Collection Wall**
A gallery screen showing all albums as vinyl records stacked on a virtual shelf — like Discogs. Tap a record to pull it out with a satisfying animation and start playing. Much more immersive than the current list/browser.

---

### X. **Partner Music Compatibility Score**
Analyze both partners' listening history (song titles/artists) and calculate a "music compatibility score" — show it as a fun percentage in the Sweet Corner tab. "You and [Name] are 78% in tune 🎶".

---

### Y. **Love Card "Time Capsule"**
Create a Love Card deck scheduled to be revealed on a future date (anniversary, birthday). Both partners see a locked card with a countdown timer, and it auto-unlocks on the date — like a digital love letter left for the future.

---

## 📝 Priority Summary

| Priority | Item | Effort |
|----------|------|--------|
| 🔴 Fix Now | Audio Focus Management (#5) | Low |
| 🔴 Fix Now | Remove debug logs (#8, #9) | Low |
| 🔴 Fix Now | Deprecated APIs (#3, #4) | Low |
| 🟡 Fix Soon | Seek position restore (#11) | Low |
| 🟡 Fix Soon | Sparkle capture stub (#7) | Medium |
| 🟡 Fix Soon | Offline scribble queue (Q) | Medium |
| 🟢 Add Next | Repeat / Shuffle modes (A) | Low |
| 🟢 Add Next | Sleep Timer (B) | Low |
| 🟢 Add Next | Share song with partner (G) | Low |
| 🟢 Add Next | Album Art → Auto Theme (E) | Medium |
| ⭐ Creative | Heartbeat Mode (U) | High |
| ⭐ Creative | Love Card Time Capsule (Y) | Medium |
| ⭐ Creative | Vinyl Collection Wall (W) | High |
