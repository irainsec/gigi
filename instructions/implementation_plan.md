# Connected Screensaver Implementation Plan

## Overview
Transform the existing Screensaver screen into a fully functional connected ambient communication system where two users can share hand-drawn scribbles.

---

## Phase 1: Core Architecture & Data Models

### New Files to Create

#### `app/src/main/java/com/aman/gigi/data/model/`
- **Connection.kt** - Connection state model
- **Scribble.kt** - Scribble data model
- **Stroke.kt** - Individual stroke data

#### `app/src/main/java/com/aman/gigi/data/local/`
- **ScribbleDatabase.kt** - Room database
- **ConnectionDao.kt** - Connection data access
- **ScribbleDao.kt** - Scribble queue data access

#### `app/src/main/java/com/aman/gigi/data/repository/`
- **ConnectionRepository.kt** - Manage connections
- **ScribbleRepository.kt** - Manage scribbles

#### `app/src/main/java/com/aman/gigi/network/`
- **WebSocketClient.kt** - Real-time communication
- **ScribbleApi.kt** - API interface

---

## Phase 2: Connection System

### Screens to Build

#### `app/src/main/java/com/aman/gigi/ui/screensaver/connection/`
- **NotConnectedScreen.kt** - Initial state, prompt to connect
- **CreateConnectionScreen.kt** - Generate QR/code
- **JoinConnectionScreen.kt** - Scan/enter code
- **ConnectingScreen.kt** - Loading state

### Features
- QR code generation using ZXing library
- QR code scanner
- 8-character alphanumeric code generation
- Connection validation via WebSocket

---

## Phase 3: Drawing Canvas

### Files to Create

#### `app/src/main/java/com/aman/gigi/ui/screensaver/drawing/`
- **DrawingCanvas.kt** - Main canvas composable
- **DrawingViewModel.kt** - Canvas state management
- **StrokeRenderer.kt** - Stroke rendering logic
- **DrawingTools.kt** - Tool selection UI

### Features
- Touch input with velocity tracking
- Catmull-Rom spline smoothing
- Color palette (8 colors)
- Pen and eraser tools
- Two-finger tap undo
- Stroke limit warnings

---

## Phase 4: Scribble Transmission

### Files to Create

#### `app/src/main/java/com/aman/gigi/data/sync/`
- **ScribbleSyncManager.kt** - Sync orchestration
- **ScribbleSerializer.kt** - JSON serialization
- **CompressionUtil.kt** - Gzip compression

### Features
- Serialize strokes to JSON
- Compress with Gzip
- Queue locally when offline
- Exponential backoff retry
- Send via WebSocket

---

## Phase 5: Scribble Playback

### Files to Create

#### `app/src/main/java/com/aman/gigi/ui/screensaver/playback/`
- **ScribblePlayback.kt** - Playback composable
- **PlaybackViewModel.kt** - Playback state
- **StrokeAnimator.kt** - Animation logic

### Features
- Stroke-by-stroke animation
- Timing based on original speed
- Easing functions (ease-in, ease-out)
- Fade animations
- Queue management (max 3)

---

## Phase 6: System Integration

### Files to Create

#### `app/src/main/java/com/aman/gigi/service/`
- **ScreensaverService.kt** - Background service
- **IdleDetector.kt** - Idle state detection
- **WakeLockManager.kt** - Screen wake management

### Features
- Detect device idle state
- Wake screen for scribbles
- Lock screen overlay (with permission)
- Charging detection
- Battery saver integration

---

## Phase 7: Updated Screensaver Screen

### Modify Existing File
**`app/src/main/java/com/aman/gigi/ui/Screensaver.kt`**

Transform from static screensaver to:
- Connection state aware
- Show partner name when connected
- Tap to open drawing canvas
- Display incoming scribbles
- Handle all states (not connected, connected idle, drawing, playback)

---

## Phase 8: Settings & Privacy

### Files to Create

#### `app/src/main/java/com/aman/gigi/ui/settings/`
- **ScreensaverSettings.kt** - Settings screen
- **ConnectionManagement.kt** - Manage connection
- **PrivacyControls.kt** - Privacy settings

### Features
- Enable/disable screensaver
- View connection status
- Disconnect option
- Brightness controls
- Battery limits
- Data cleanup

---

## Dependencies to Add

### `app/build.gradle.kts`
```kotlin
dependencies {
    // WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // QR Code
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Gson for JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
```

---

## Implementation Order

### Week 1: Foundation
1. Create data models
2. Set up Room database
3. Create repositories
4. Set up dependency injection

### Week 2: Connection
1. Build connection UI screens
2. Implement QR code generation/scanning
3. Set up WebSocket client
4. Test connection flow

### Week 3: Drawing
1. Build drawing canvas
2. Implement stroke smoothing
3. Add drawing tools
4. Test drawing performance

### Week 4: Transmission & Playback
1. Implement serialization/compression
2. Build send logic with queue
3. Create playback animations
4. Test end-to-end flow

### Week 5: System Integration
1. Add idle detection
2. Implement wake locks
3. Lock screen integration
4. Battery handling

### Week 6: Polish & Testing
1. Settings screens
2. Privacy controls
3. Edge case testing
4. Performance optimization

---

## Testing Strategy

### Unit Tests
- Stroke serialization/deserialization
- Compression/decompression
- Connection code generation
- Queue management

### Integration Tests
- WebSocket connection
- Database operations
- End-to-end scribble flow

### Manual Testing
- Offline scenarios
- App killed/reboot
- Battery saver mode
- Lock screen behavior
- Multiple scribbles queue

---

## Success Criteria

✅ Two users can connect via QR/code  
✅ Drawing canvas is smooth and responsive  
✅ Scribbles send/receive in <500ms when online  
✅ Offline queue works correctly  
✅ Playback animation is smooth and natural  
✅ Lock screen integration works  
✅ Battery impact is minimal  
✅ All edge cases handled gracefully  
✅ Privacy controls work correctly  
✅ No crashes or data loss  

---

## Next Steps

1. Review and approve this plan
2. Start with Phase 1: Create data models
3. Iterate through each phase
4. Test thoroughly at each stage
5. Deploy and monitor
