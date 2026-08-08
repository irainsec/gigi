# Connected Screensaver: Complete Product Specification

## 1. PRODUCT DEFINITION

### Purpose
A silent, ambient communication channel between two people who want to share presence without the noise of traditional messaging. The Screensaver transforms idle moments into opportunities for gentle, emotional connection through hand-drawn scribbles.

### Emotional Intent
- **Calm presence**: "I'm thinking of you" without demanding attention
- **Playful intimacy**: Shared private moments through simple drawings
- **Asynchronous affection**: Connection that respects boundaries and time
- **Ambient awareness**: Knowing someone is there, without constant pings
- **Gentle surprise**: Unexpected moments of joy during idle time

### User Mental Model
"My phone's screensaver is a shared canvas with one special person. When I'm idle, I might see their latest scribble appear as if they're drawing it right now. When I want to reach out, I draw something simple and send it to their screensaver."

### What This Feature Intentionally Avoids
- ❌ Chat/messaging paradigm
- ❌ Notifications, badges, alerts
- ❌ Read receipts or delivery confirmations
- ❌ Multiple connections or group features
- ❌ Text, emojis, stickers, or media
- ❌ Conversation threads or history
- ❌ Timestamps or "last seen"
- ❌ Urgency or FOMO mechanics
- ❌ Social features (likes, reactions, sharing)

---

## 2. COMPLETE USER STATE MACHINE

### State Diagram
```
[First Launch] → [Onboarding] → [Not Connected]
                                      ↓
                            [Connection Flow] ← → [Connecting]
                                      ↓
                              [Connected Idle] ← → [Partner Disconnected]
                                      ↓
                    ┌─────────────────┼─────────────────┐
                    ↓                 ↓                 ↓
            [Drawing Mode]    [Receiving Scribble]  [Idle Display]
                    ↓                 ↓
            [Sending Scribble]  [Playing Scribble]
                    ↓                 ↓
            [Connected Idle] ← [Fade to Idle]
                    ↑
                    └─────── [Error States] ──────┘
```

### State Definitions

#### **First-Time User**
- **Entry**: App installed, never opened
- **State**: No connection, no data
- **Actions**: Show onboarding
- **Exit**: Complete onboarding → Not Connected

#### **Not Connected**
- **Entry**: Onboarding complete, no partner
- **State**: Waiting for connection
- **Display**: Empty screensaver with "Connect with someone" prompt
- **Actions**: Initiate connection (QR/code)
- **Exit**: Connection initiated → Connecting

#### **Connecting**
- **Entry**: User initiated connection
- **State**: Waiting for partner acceptance
- **Display**: Loading state with connection code/QR
- **Actions**: Wait, cancel
- **Exit Success**: Partner accepts → Connected Idle
- **Exit Failure**: Timeout/rejection → Not Connected

#### **Connected Idle**
- **Entry**: Connection established, no activity
- **State**: Screensaver shows partner name, calm ambient UI
- **Display**: Minimal UI, partner name, subtle animations
- **Actions**: Tap to draw, receive scribble, disconnect
- **Exit**: User taps → Drawing Mode | Scribble arrives → Receiving Scribble

#### **Drawing Mode**
- **Entry**: User taps screensaver to draw
- **State**: Canvas active, drawing tools visible
- **Display**: Full-screen canvas, minimal tools
- **Actions**: Draw, erase, change color, send, cancel
- **Exit**: Send → Sending Scribble | Cancel → Connected Idle

#### **Sending Scribble**
- **Entry**: User confirms send
- **State**: Uploading scribble data
- **Display**: Brief sending animation
- **Actions**: Wait
- **Exit**: Success → Connected Idle | Failure → Drawing Mode (retry)

#### **Receiving Scribble**
- **Entry**: Partner's scribble arrives
- **State**: Preparing playback
- **Display**: Screensaver wakes if idle
- **Actions**: Queue scribble
- **Exit**: Ready → Playing Scribble

#### **Playing Scribble**
- **Entry**: Scribble ready to display
- **State**: Animating stroke-by-stroke
- **Display**: Scribble draws itself on screensaver
- **Actions**: Watch, dismiss early (optional)
- **Exit**: Complete → Fade to Idle

#### **Fade to Idle**
- **Entry**: Scribble playback complete
- **State**: Scribble fades out
- **Display**: Gentle fade animation
- **Actions**: Wait
- **Exit**: Fade complete → Connected Idle

#### **Partner Disconnected**
- **Entry**: Partner ended connection
- **State**: Connection lost
- **Display**: "Connection ended" message
- **Actions**: Acknowledge
- **Exit**: User acknowledges → Not Connected

#### **Error States**
- **Connection Lost**: Network error → Show subtle indicator, auto-retry
- **Sync Failed**: Scribble failed to send → Retry option
- **App Killed**: Resume to last known state
- **Permission Denied**: Show settings prompt

---

## 3. FULL SCREEN LIST (EXHAUSTIVE)

### 3.1 Onboarding Screens

#### **Welcome Screen**
- **Purpose**: Introduce the concept
- **Entry**: First app launch
- **Exit**: User taps "Continue" → Feature Explanation

#### **Feature Explanation Screen**
- **Purpose**: Explain how Screensaver works
- **Entry**: From Welcome
- **Exit**: User taps "Next" → Connection Explanation

#### **Connection Explanation Screen**
- **Purpose**: Explain one-to-one connection model
- **Entry**: From Feature Explanation
- **Exit**: User taps "Get Started" → Not Connected State

### 3.2 Connection Screens

#### **Not Connected Screen**
- **Purpose**: Prompt user to connect
- **Entry**: No active connection
- **Exit**: Tap "Connect" → Connection Method Choice

#### **Connection Method Choice Screen**
- **Purpose**: Choose how to connect (create/join)
- **Entry**: From Not Connected
- **Exit**: Create Code → Show QR | Enter Code → Code Input

#### **Show QR/Code Screen**
- **Purpose**: Display connection code for partner
- **Entry**: User chooses "Create Connection"
- **Exit**: Partner connects → Connected Idle | Cancel → Not Connected

#### **Enter Code Screen**
- **Purpose**: Input partner's connection code
- **Entry**: User chooses "Join Connection"
- **Exit**: Valid code → Connecting | Cancel → Not Connected

#### **Connecting Screen**
- **Purpose**: Show connection in progress
- **Entry**: Code submitted
- **Exit**: Success → Connected Idle | Timeout → Connection Failed

#### **Connection Failed Screen**
- **Purpose**: Handle connection errors
- **Entry**: Connection timeout or error
- **Exit**: Retry → Connection Method Choice | Cancel → Not Connected

### 3.3 Main Screensaver Screens

#### **Connected Idle Screensaver**
- **Purpose**: Default ambient state when connected
- **Entry**: Connection established, no activity
- **Exit**: Tap → Drawing Canvas | Scribble arrives → Scribble Playback

#### **Drawing Canvas Screen**
- **Purpose**: Create scribble to send
- **Entry**: User taps screensaver
- **Exit**: Send → Sending Animation | Cancel → Connected Idle

#### **Sending Animation Screen**
- **Purpose**: Brief feedback while sending
- **Entry**: User taps "Send"
- **Exit**: Success → Connected Idle | Error → Send Error

#### **Scribble Playback Screen**
- **Purpose**: Display incoming scribble
- **Entry**: Scribble received
- **Exit**: Playback complete → Fade to Idle

#### **Fade to Idle Screen**
- **Purpose**: Transition from scribble to idle
- **Entry**: Scribble playback complete
- **Exit**: Fade complete → Connected Idle

### 3.4 Settings & Management Screens

#### **Screensaver Settings Screen**
- **Purpose**: Configure screensaver behavior
- **Entry**: User opens settings
- **Exit**: Back → Previous screen

#### **Connection Management Screen**
- **Purpose**: View/disconnect current connection
- **Entry**: From settings
- **Exit**: Disconnect → Disconnect Confirmation | Back → Settings

#### **Disconnect Confirmation Screen**
- **Purpose**: Confirm disconnection
- **Entry**: User taps disconnect
- **Exit**: Confirm → Not Connected | Cancel → Connection Management

### 3.5 System Integration Screens

#### **Lock Screen Overlay**
- **Purpose**: Show scribbles on locked device
- **Entry**: Device locked, scribble arrives
- **Exit**: Device unlocked → App Screensaver

#### **Charging Screensaver**
- **Purpose**: Enhanced screensaver when charging
- **Entry**: Device charging
- **Exit**: Unplugged → Normal Screensaver

---

## 4. DETAILED UI & UX FOR EACH SCREEN

### 4.1 Welcome Screen

**Layout Structure**
```
┌─────────────────────────┐
│                         │
│    [Animated Icon]      │
│                         │
│   "Connected            │
│    Screensaver"         │
│                         │
│   Brief tagline         │
│                         │
│                         │
│     [Continue]          │
│                         │
└─────────────────────────┘
```

**UI Elements**
- Animated logo/icon (gentle pulse)
- App name in elegant typography
- Tagline: "Share presence, not messages"
- Primary button: "Continue"

**Gestures**
- Tap "Continue" to proceed
- Swipe up for accessibility options

**Animations**
- Logo gentle breathing animation
- Fade-in on load
- Button subtle hover state

**Accessibility**
- High contrast mode support
- Screen reader labels
- Large touch targets (48dp minimum)

### 4.2 Connected Idle Screensaver

**Layout Structure**
```
┌─────────────────────────┐
│                         │
│   [Floating Orbs]       │
│                         │
│      Partner Name       │
│                         │
│   [Subtle Indicator]    │
│                         │
│                         │
│   Tap to draw           │
│                         │
└─────────────────────────┘
```

**UI Elements**
- Partner name (centered, elegant font)
- Floating ambient orbs (glassmorphic)
- Subtle "Tap to draw" hint (fades after first use)
- Connection status indicator (tiny, unobtrusive)
- Time display (optional, minimal)

**Gestures**
- Single tap anywhere → Open drawing canvas
- Long press → Quick settings
- Swipe down → Dismiss to home

**Animations**
- Orbs float slowly (3-6 second loops)
- Partner name gentle pulse
- Hint text fade in/out
- Smooth transitions to drawing mode

**Accessibility**
- VoiceOver: "Connected with [Partner Name]. Tap to draw."
- High contrast: Increase orb opacity
- Reduce motion: Static orbs

### 4.3 Drawing Canvas Screen

**Layout Structure**
```
┌─────────────────────────┐
│ [Cancel]      [Send]    │
│                         │
│                         │
│                         │
│    Drawing Canvas       │
│                         │
│                         │
│                         │
│ [Tools: Pen|Eraser|🎨] │
└─────────────────────────┘
```

**UI Elements**
- Full-screen canvas (white/dark based on theme)
- Top bar: Cancel (left), Send (right)
- Bottom toolbar: Pen, Eraser, Color picker
- Stroke preview (shows current color/size)
- Minimal, non-distracting UI

**Gestures**
- Draw with finger/stylus
- Two-finger tap → Undo last stroke
- Pinch → Zoom (disabled to keep simple)
- Swipe from edge → Cancel

**Animations**
- Canvas fade-in
- Tool selection highlight
- Send button pulse when drawing exists
- Smooth stroke rendering

**Accessibility**
- Large tool buttons
- VoiceOver: "Drawing canvas. Tap to draw."
- Haptic feedback on tool selection
- High contrast mode for tools

### 4.4 Scribble Playback Screen

**Layout Structure**
```
┌─────────────────────────┐
│                         │
│                         │
│                         │
│   [Scribble Animates]   │
│                         │
│                         │
│                         │
│   From: Partner Name    │
└─────────────────────────┘
```

**UI Elements**
- Full-screen scribble display
- Subtle "From: [Partner]" label (bottom)
- Progress indicator (optional, subtle)
- No dismiss button (auto-fades)

**Gestures**
- Tap to dismiss early (optional)
- No other interactions

**Animations**
- Stroke-by-stroke playback (matches drawing speed)
- Gentle fade-in of each stroke
- Hold for 3-5 seconds after complete
- Fade out to idle (2 second transition)

**Accessibility**
- VoiceOver: "Scribble from [Partner Name]"
- Describe drawing if possible (future: ML description)
- Haptic pattern on arrival

---

## 5. SCRIBBLE SYSTEM DESIGN (CORE FEATURE)

### Canvas Behavior

**Technical Specs**
- Canvas size: Device screen resolution (scaled for performance)
- Coordinate system: Normalized 0-1 (device-independent)
- Background: Adaptive (light/dark mode)
- Max canvas memory: 2MB per scribble

**Drawing Engine**
- Technology: Android Canvas API with hardware acceleration
- Rendering: Path-based (not bitmap)
- Anti-aliasing: Enabled for smooth strokes
- Layer: Single layer (no complexity)

### Stroke Physics

**Smoothing Algorithm**
```
Catmull-Rom spline interpolation
- Input: Raw touch points
- Output: Smooth bezier curves
- Sampling: 60fps minimum
- Latency: <16ms touch-to-render
```

**Speed Adaptation**
- Fast strokes: Thinner, lighter
- Slow strokes: Thicker, more pressure
- Velocity calculation: Distance between points / time delta
- Pressure simulation: Map velocity to stroke width (2dp - 8dp)

**Stroke Properties**
- Width range: 2dp (fast) to 8dp (slow)
- Opacity: 0.9 (consistent)
- Cap style: Round
- Join style: Round
- Color: User-selected from palette

### Tools

**Pen Tool**
- Default tool
- Stroke width: Velocity-based (2-8dp)
- Color: From palette
- Behavior: Additive (strokes layer)

**Eraser Tool**
- Mode: Stroke-based (erase entire strokes, not pixels)
- Activation: Tap eraser icon
- Visual: Highlight strokes on hover
- Undo: Can undo erasure

**Color Palette**
- Preset colors: 8 carefully chosen hues
  - Black, White, Red, Blue, Green, Yellow, Purple, Orange
- Selection: Bottom toolbar
- Indicator: Current color shown on pen icon
- No custom colors (keep simple)

### Send / Cancel Flow

**Send Flow**
1. User taps "Send" button
2. Validate: Check if canvas has strokes
3. Serialize: Convert paths to JSON format
4. Compress: Gzip compression
5. Upload: Send to server via WebSocket
6. Feedback: Brief "Sent" animation
7. Clear: Canvas clears, return to idle

**Cancel Flow**
1. User taps "Cancel" or swipes from edge
2. Confirm: "Discard scribble?" (if strokes exist)
3. Clear: Canvas clears
4. Return: Back to Connected Idle

**Undo/Redo**
- Undo: Two-finger tap (removes last stroke)
- Redo: Not implemented (keep simple)
- Limit: Last 20 strokes in memory

### Performance Limits

**Stroke Limits**
- Max strokes per scribble: 100
- Max points per stroke: 500
- Max total points: 10,000
- Warning: Subtle indicator at 80% capacity

**File Size Limits**
- Max serialized size: 100KB
- Compression: Gzip (typically 10:1 ratio)
- Rejection: If >100KB, prompt to simplify

**Memory Management**
- Canvas bitmap: Recycled on exit
- Stroke history: Cleared on send
- Undo stack: Max 20 strokes
- Garbage collection: Triggered on low memory

---

## 6. SCRIBBLE PLAYBACK (RECEIVER SIDE)

### How Screensaver Wakes

**Trigger Conditions**
- Scribble arrives via WebSocket
- Device is idle (screensaver active)
- OR device is locked (show on lock screen)
- OR device is charging (enhanced display)

**Wake Sequence**
1. Receive scribble data
2. Parse and validate JSON
3. Check device state (idle/locked/active)
4. If idle/locked: Wake screensaver
5. If active: Queue for next idle moment
6. Prepare playback (deserialize paths)

**Display Priority**
- Lock screen: Highest (show immediately)
- Charging: High (show immediately)
- Idle screensaver: Medium (show immediately)
- Active use: Low (queue for later)

### Stroke-by-Stroke Replay Logic

**Playback Algorithm**
```kotlin
for each stroke in scribble:
    for each point in stroke:
        interpolate between points
        draw segment with easing
        wait for timing interval
    complete stroke
    brief pause (50ms)
next stroke
```

**Timing Calculation**
- Original drawing speed: Captured during creation
- Playback speed: 1.2x original (slightly faster for smoothness)
- Min interval: 16ms (60fps)
- Max interval: 100ms (prevent too slow)

**Interpolation**
- Between points: Linear interpolation
- Easing: Ease-in-out for natural feel
- Smoothing: Same Catmull-Rom as drawing

### Timing, Easing, Fading

**Stroke Timing**
- Fast strokes: Play back faster
- Slow strokes: Play back slower (but capped)
- Pause between strokes: 50ms
- Total playback time: 5-30 seconds (typical)

**Easing Functions**
- Stroke appearance: Ease-in (0-200ms)
- Stroke drawing: Linear (natural)
- Stroke completion: Ease-out (last 100ms)
- Fade to idle: Ease-out (2 seconds)

**Fading Sequence**
1. Scribble complete: Hold for 3 seconds
2. Begin fade: Reduce opacity 1.0 → 0.0
3. Duration: 2 seconds
4. Easing: Ease-out cubic
5. Complete: Return to Connected Idle

### Duration on Screen

**Display Time**
- Playback duration: Variable (based on complexity)
- Hold after complete: 3 seconds
- Fade out: 2 seconds
- Total minimum: 8 seconds
- Total maximum: 40 seconds

**User Control**
- Tap to dismiss: Allowed after 2 seconds
- Auto-dismiss: After hold + fade
- No replay: Scribble shown once only

### Handling Multiple Incoming Scribbles

**Queue System**
- Max queue size: 3 scribbles
- Overflow: Oldest scribble dropped
- Display: One at a time, sequential
- Interval: 5 seconds between scribbles

**Queue Behavior**
```
Queue: [Scribble1, Scribble2, Scribble3]
1. Play Scribble1 → Complete → Fade
2. Wait 5 seconds in idle
3. Play Scribble2 → Complete → Fade
4. Wait 5 seconds in idle
5. Play Scribble3 → Complete → Fade
6. Return to normal idle
```

**Edge Cases**
- New scribble during playback: Add to queue
- Queue full: Drop oldest, add newest
- User dismisses: Skip to next in queue
- Connection lost: Finish current, clear queue

---

## 7. CONNECTIVITY & SYNC LOGIC

### Connection Methods

**QR Code Method**
1. User A: Tap "Create Connection"
2. App generates unique connection code (8-char alphanumeric)
3. Display QR code + text code
4. User B: Tap "Join Connection"
5. Scan QR or enter code manually
6. Server validates code
7. Both users connected

**Manual Code Method**
1. User A: Generate code
2. Share code via any channel (text, voice, etc.)
3. User B: Enter code in app
4. Server validates and connects

**Security**
- Code format: 8 characters (A-Z, 0-9, excluding ambiguous chars)
- Expiry: 24 hours
- One-time use: Code invalid after connection
- Encryption: End-to-end for scribble data

### Online Behavior

**Active Connection**
- WebSocket: Persistent connection to server
- Heartbeat: Ping every 30 seconds
- Reconnect: Auto-retry on disconnect (exponential backoff)
- Status: Real-time sync of partner online/offline

**Scribble Transmission**
1. User sends scribble
2. Upload to server via WebSocket
3. Server validates and stores
4. Server pushes to partner's device
5. Partner receives and displays
6. Acknowledgment sent back
7. Server deletes scribble (ephemeral)

**Latency Optimization**
- WebSocket: Low-latency protocol
- Compression: Gzip for smaller payload
- CDN: Geo-distributed servers
- Target latency: <500ms send-to-receive

### Offline Behavior

**Sender Offline**
- Scribble saved locally
- Upload when connection restored
- Max offline queue: 5 scribbles
- Indicator: Subtle "Sending when online"

**Receiver Offline**
- Server stores scribble (max 24 hours)
- Deliver when receiver comes online
- Max stored: 3 scribbles per user
- Older scribbles: Dropped

**Both Offline**
- Sender: Queue locally
- Server: Hold for receiver
- Sync when both online
- Graceful degradation

### Queueing & Retry

**Send Queue**
- Local storage: SQLite database
- Max queue: 5 scribbles
- Retry logic: Exponential backoff (1s, 2s, 4s, 8s, 16s)
- Max retries: 5 attempts
- Failure: Notify user subtly

**Receive Queue**
- Server-side: Redis queue
- Max hold: 24 hours
- Max scribbles: 3 per user
- Delivery: FIFO order

### Failure Handling

**Send Failures**
- Network error: Auto-retry
- Server error: Retry with backoff
- Timeout: Retry up to 5 times
- Permanent failure: Show "Couldn't send" message
- User action: Retry or discard

**Receive Failures**
- Corrupt data: Discard silently
- Parse error: Log and skip
- Display error: Fallback to idle
- No user notification (silent failure)

### Reconnection Logic

**Auto-Reconnect**
```
Connection lost:
1. Attempt reconnect immediately
2. Wait 1 second → Retry
3. Wait 2 seconds → Retry
4. Wait 4 seconds → Retry
5. Wait 8 seconds → Retry
6. Wait 16 seconds → Retry
7. Max wait: 32 seconds between retries
8. Continue indefinitely (with backoff cap)
```

**User Indication**
- Subtle indicator: Small dot (gray = offline, green = online)
- No intrusive notifications
- Status visible in settings only

### NO Notifications Allowed

**Strict Rules**
- ❌ No push notifications
- ❌ No badges
- ❌ No sounds
- ❌ No vibrations
- ❌ No lock screen alerts (except scribble display)
- ✅ Only: Scribble appears on screensaver silently

**Rationale**
- Preserve calm, ambient nature
- Avoid notification fatigue
- Respect user's attention
- Maintain emotional intent

---

## 8. SYSTEM-LEVEL SCREENSAVER BEHAVIOR

### Lock Screen Behavior

**Integration**
- Android: Custom lock screen overlay (requires permission)
- Display: Scribble playback on lock screen
- Security: No unlock required to view
- Privacy: User can disable in settings

**Lock Screen Flow**
1. Device locked
2. Scribble arrives
3. Wake screen (dim brightness)
4. Display scribble playback
5. Fade out
6. Return to lock screen

**Permissions Required**
- `SYSTEM_ALERT_WINDOW`: Draw over lock screen
- `WAKE_LOCK`: Wake screen for scribble
- User consent: Explicit permission request

### Charging Behavior

**Enhanced Mode**
- Trigger: Device plugged in and charging
- Display: Brighter, more vibrant
- Frequency: More responsive to scribbles
- Battery: No concern (plugged in)

**Charging Screensaver**
- Always-on: Screensaver stays active while charging
- Brightness: 50% (adjustable in settings)
- Scribbles: Display immediately
- Idle: Show partner name and ambient animations

### Idle Timeout

**Idle Detection**
- Trigger: No user interaction for X minutes (user-configurable)
- Default: 2 minutes
- Range: 1-10 minutes
- Override: Disabled when charging

**Screensaver Activation**
1. Idle timeout reached
2. Fade current screen
3. Show Connected Idle screensaver
4. Listen for scribbles
5. Deactivate on touch

### Brightness & Dimming

**Adaptive Brightness**
- Ambient light sensor: Adjust brightness
- Night mode: Dim to 10% after 10pm (user time)
- Day mode: Normal brightness
- Charging: 50% brightness

**Dimming Schedule**
- After scribble: Hold 3 seconds → Dim to 30%
- After 30 seconds idle: Dim to 10%
- After 2 minutes: Screen off (if not charging)
- User touch: Restore full brightness

### Battery Saver Handling

**Low Battery Mode**
- Trigger: Battery <20%
- Behavior: Reduce screensaver activity
- Scribbles: Queue for later (don't wake screen)
- Indicator: "Battery saver active" in settings

**Battery Optimization**
- Background: Doze mode compatible
- Wake locks: Minimal usage
- Network: Efficient WebSocket
- Rendering: Hardware-accelerated only

### Interruptions (Calls, Alarms)

**Incoming Call**
- Priority: Call takes precedence
- Scribble: Pause playback
- Resume: After call ends (if still relevant)
- Queue: Hold scribble for later

**Alarm**
- Priority: Alarm takes precedence
- Scribble: Dismiss immediately
- Queue: Scribble lost (ephemeral nature)
- No replay: User missed it

**Other Interruptions**
- Notifications: Screensaver pauses
- App switch: Screensaver exits
- Home button: Screensaver exits
- Resume: Return to Connected Idle when idle again

---

## 9. PRIVACY & SAFETY MODEL

### Consent & Disconnect

**Connection Consent**
- Both users must explicitly agree
- No automatic connections
- Clear explanation before connecting
- Easy disconnect option

**Disconnect Flow**
1. User taps "Disconnect" in settings
2. Confirmation: "End connection with [Partner]?"
3. Confirm: Connection terminated
4. Notify partner: Silent (no notification, just disconnected state)
5. Data: All scribbles deleted immediately

**Mutual Respect**
- Either user can disconnect anytime
- No questions asked
- No guilt mechanics
- Clean break

### Data Retention Rules

**Scribble Lifecycle**
1. Created: Stored locally until sent
2. Sent: Uploaded to server
3. Delivered: Pushed to receiver
4. Displayed: Shown once
5. Deleted: Removed from server immediately after delivery
6. Local: Cleared after display

**Server Storage**
- Ephemeral: Scribbles deleted after delivery
- Max hold: 24 hours (if receiver offline)
- Encryption: AES-256 at rest
- Logs: No scribble content logged

**Local Storage**
- Send queue: Encrypted SQLite
- No history: No scribble archive
- Clear on disconnect: All data deleted
- No backups: Scribbles not backed up

### Scribble Expiry

**Time-Based Expiry**
- Server: 24 hours max
- Local queue: 7 days max
- Display: Once only, then deleted
- No replay: Cannot view again

**Event-Based Expiry**
- Disconnect: All scribbles deleted
- App uninstall: All data removed
- Account deletion: All data purged

### User Control

**Privacy Settings**
- Lock screen display: On/Off
- Charging mode: On/Off
- Idle timeout: 1-10 minutes
- Brightness: 10-100%
- Disconnect: One-tap access

**Data Control**
- View connection: See partner name
- Disconnect: End connection
- Clear queue: Delete pending scribbles
- No export: Cannot save scribbles

### Screenshot & Misuse Considerations

**Screenshot Detection**
- Monitor: Detect screenshot events
- Action: None (allow screenshots)
- Rationale: Trust-based system, not DRM

**Misuse Prevention**
- Report: Option to report abuse (future)
- Block: Disconnect prevents further contact
- No content moderation: Trust between users
- Terms of service: Clear guidelines

**Trust Model**
- One-to-one: Inherent trust assumed
- No strangers: Connection requires code sharing
- Consent: Both users must agree
- Easy exit: Disconnect anytime

---

## 10. SETTINGS & USER CONTROL

### Screensaver Enable/Disable

**Master Toggle**
- Location: Settings → Screensaver
- Default: On (after connection)
- Effect: Completely disable screensaver feature
- Scribbles: Still received but not displayed (queued)

### Allowed User Management

**Connection Management**
- View: Current partner name
- Status: Online/offline indicator
- Disconnect: End connection button
- Reconnect: Not allowed (must create new connection)

**Connection History**
- No history: Only current connection shown
- Privacy: Past connections not stored

### Scribble Duration

**Display Time Settings**
- Hold duration: 2-5 seconds (after playback)
- Fade duration: 1-3 seconds
- Default: 3 seconds hold, 2 seconds fade
- User preference: Adjustable slider

### Brightness Control

**Screensaver Brightness**
- Range: 10-100%
- Default: 50% (charging), 30% (idle)
- Night mode: Auto-dim after 10pm
- Adaptive: Use ambient light sensor

**Brightness Schedule**
- Day (6am-10pm): User-set brightness
- Night (10pm-6am): 50% of day brightness
- Override: Manual adjustment anytime

### Battery Limits

**Battery Saver Integration**
- Trigger: Battery <20%
- Behavior: Disable screensaver wake
- Scribbles: Queue for later
- Indicator: "Battery saver active"

**Custom Battery Limit**
- User-set threshold: 10-50%
- Default: 20%
- Effect: Disable screensaver below threshold
- Resume: Auto-enable when charging

---

## 11. EDGE CASES & FAIL-SAFES

### App Killed

**Scenario**: User force-closes app or system kills it

**Handling**
1. Save state: Current connection, pending scribbles
2. Background service: Minimal service for WebSocket
3. Restart: Auto-restart on scribble arrival (if allowed)
4. Notification: None (silent restart)
5. Resume: Return to last state

**Data Integrity**
- Pending sends: Saved to local DB
- Connection: Reconnect on restart
- Queue: Preserved in SQLite

### Device Reboot

**Scenario**: Device restarts

**Handling**
1. Boot receiver: Restart app on boot (if enabled)
2. Reconnect: Auto-reconnect to server
3. Sync: Fetch missed scribbles (max 3)
4. Resume: Return to Connected Idle

**Permissions**
- `RECEIVE_BOOT_COMPLETED`: Required
- User consent: Explained in onboarding

### Connection Lost Mid-Draw

**Scenario**: Network drops while drawing

**Handling**
1. Continue drawing: Canvas still works
2. Send attempt: Queue locally
3. Indicator: Subtle "Offline" indicator
4. Auto-send: When connection restored
5. User feedback: "Will send when online"

**User Experience**
- No interruption to drawing
- Seamless queue and send
- No data loss

### Large Scribbles

**Scenario**: User creates very complex scribble

**Handling**
1. Monitor: Track stroke count and points
2. Warning: At 80% capacity, show subtle indicator
3. Limit: At 100%, disable new strokes
4. Message: "Scribble too complex, please simplify"
5. Options: Erase strokes or send as-is (if under size limit)

**Prevention**
- Stroke limit: 100 strokes
- Point limit: 10,000 points
- Size limit: 100KB serialized

### Corrupt Data

**Scenario**: Received scribble data is corrupted

**Handling**
1. Validate: Check JSON structure
2. Parse: Attempt to parse paths
3. Fail gracefully: If corrupt, discard silently
4. Log: Error logged for debugging
5. No user notification: Silent failure

**Fallback**
- Display: Skip to next scribble in queue
- Retry: No retry (ephemeral nature)
- User: No indication of failure

### Low Battery

**Scenario**: Battery drops below threshold during scribble

**Handling**
1. Check: Monitor battery level
2. Interrupt: If <10%, pause playback
3. Queue: Save scribble for later
4. Resume: When charging or battery >20%
5. Indicator: None (silent handling)

### Permission Revoked

**Scenario**: User revokes lock screen or wake lock permission

**Handling**
1. Detect: Check permissions on scribble arrival
2. Fallback: Queue scribble, don't wake screen
3. Prompt: Show settings prompt (non-intrusive)
4. Graceful: Continue working without lock screen feature
5. Settings: Explain why permission needed

**User Communication**
- In-app message: "Enable lock screen to see scribbles when locked"
- Settings link: Direct link to permission settings
- No blocking: App still works without permission

---

## 12. FINAL END-TO-END USER JOURNEY

### Complete Experience Flow

#### **Install → Connect**

**Day 1: Installation**
1. User downloads app from Play Store
2. Opens app for first time
3. Sees Welcome screen with gentle animation
4. Taps "Continue"
5. Reads Feature Explanation: "Share presence through scribbles"
6. Taps "Next"
7. Reads Connection Explanation: "Connect with one person"
8. Taps "Get Started"
9. Arrives at Not Connected screen

**Day 1: Connection**
10. User taps "Connect with someone"
11. Chooses "Create Connection"
12. App generates QR code and 8-character code
13. User shares code with partner (via text/voice/in-person)
14. Partner opens app, taps "Join Connection"
15. Partner scans QR or enters code
16. Both users see "Connecting..." animation
17. Connection established
18. Both users see Connected Idle screensaver with partner's name

#### **Idle → Scribble**

**Day 2: First Scribble**
1. User's phone is idle on desk
2. Screensaver shows partner's name with floating orbs
3. User picks up phone, taps screensaver
4. Drawing canvas appears with smooth fade-in
5. User draws a simple heart with finger
6. Stroke appears smoothly, following finger
7. User taps "Send" button
8. Brief "Sending..." animation (500ms)
9. Canvas clears, returns to Connected Idle
10. User puts phone down

#### **Send → Receive**

**Day 2: Partner Receives**
1. Partner's phone is charging on nightstand
2. Screensaver is active (charging mode)
3. Scribble arrives via WebSocket
4. Screen wakes gently (if dimmed)
5. "From: [User]" appears briefly
6. Heart scribble begins drawing itself
7. Stroke-by-stroke animation (3 seconds)
8. Complete heart appears
9. Holds on screen for 3 seconds
10. Gentle fade out over 2 seconds
11. Returns to Connected Idle with partner's name

#### **Display → Fade**

**Day 2: Scribble Display Details**
1. First stroke appears with ease-in
2. Draws smoothly following original path
3. Second stroke begins after 50ms pause
4. Continues until complete
5. Final stroke completes with ease-out
6. Scribble holds at full opacity (3 seconds)
7. Fade begins: opacity 1.0 → 0.0 (2 seconds)
8. Scribble disappears completely
9. Floating orbs return
10. Partner's name pulses gently

#### **Idle → Ongoing Use**

**Week 1: Daily Rhythm**
- Morning: Partner sends coffee cup scribble while making breakfast
- User sees it on lock screen while phone charges overnight
- Afternoon: User sends smiley face during lunch break
- Partner sees it on idle screensaver at desk
- Evening: Partner sends star while watching TV
- User sees it when phone goes idle
- Night: Cycle repeats

**Month 1: Established Pattern**
- 2-5 scribbles per day
- No pressure, no expectations
- Gentle presence throughout day
- Moments of connection without interruption
- Calm, ambient communication

**Long-term: Emotional Bond**
- Scribbles become a language
- Inside jokes through drawings
- Comfort in seeing partner's name
- Anticipation of next scribble
- Deep connection through simplicity

---

## TECHNICAL ARCHITECTURE SUMMARY

### System Components

**Client (Android App)**
- UI Layer: Jetpack Compose
- Drawing Engine: Android Canvas API
- State Management: ViewModel + Flow
- Local Storage: SQLite + DataStore
- Network: OkHttp + WebSocket

**Server (Backend)**
- WebSocket Server: Node.js + Socket.io
- Database: Redis (ephemeral queue)
- Storage: None (fully ephemeral)
- Authentication: JWT tokens
- Encryption: TLS 1.3 + E2E encryption

**Infrastructure**
- Hosting: Cloud provider (AWS/GCP)
- CDN: Geo-distributed edge servers
- Monitoring: Error tracking, analytics
- Scaling: Auto-scaling WebSocket servers

### Data Flow

```
User A                    Server                    User B
  │                         │                         │
  ├─ Draw scribble          │                         │
  ├─ Serialize to JSON      │                         │
  ├─ Compress (gzip)        │                         │
  ├─ Send via WebSocket ────┤                         │
  │                         ├─ Validate               │
  │                         ├─ Store (Redis, 24h)     │
  │                         ├─ Push via WebSocket ────┤
  │                         │                         ├─ Receive
  │                         │                         ├─ Decompress
  │                         │                         ├─ Parse JSON
  │                         │                         ├─ Render playback
  │                         │                         ├─ Display
  │                         ├─ Delete from Redis      │
  │                         │                         ├─ Fade out
```

---

## CONCLUSION

This Connected Screensaver feature is designed to be:

✅ **Emotionally Calm**: No notifications, no urgency, no pressure  
✅ **Technically Robust**: Handles all edge cases, offline scenarios, system integration  
✅ **Privacy-Focused**: Ephemeral data, user control, easy disconnect  
✅ **Battery-Efficient**: Smart wake logic, adaptive brightness, battery saver integration  
✅ **Accessible**: VoiceOver support, high contrast, large touch targets  
✅ **Scalable**: Efficient architecture, minimal server load, geo-distributed  
✅ **Production-Ready**: Complete specification, no gaps, ready for implementation  

**This is a feature that respects users, values their time, and creates genuine emotional connection through simplicity and thoughtfulness.**
