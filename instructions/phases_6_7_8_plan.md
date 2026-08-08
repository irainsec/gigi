# Connected Screensaver - Final Phases Implementation Plan

**Phases Remaining:** 6, 7, 8  
**Current Status:** 5/8 phases complete (62.5%)  
**Build Status:** ✅ Successful

---

## Phase 6: System Integration

### Goal
Integrate the screensaver into the Android system lifecycle with proper idle detection and resource management.

### Simplified Approach
Since full system integration (lock screen overlay, charging detection) requires complex permissions and system-level access, we'll implement a **simplified version** that focuses on:

1. **Manual Activation** - User navigates to Screensaver tab
2. **Keep Screen On** - While viewing screensaver/drawing
3. **Background Sync** - Service runs when connected

### Files to Create

#### Service Integration (1 file)
- `service/ScreensaverManager.kt` - Manages screensaver lifecycle

### Changes Needed
- Start sync service when connection is established
- Stop sync service when disconnected
- Add wake lock for drawing screen

---

## Phase 7: Settings & Privacy

### Goal
Provide user control over connections and privacy settings.

### Features

#### Settings Screen (3 files)
1. **`ui/settings/ScreensaverSettingsScreen.kt`**
   - Connection status display
   - Disconnect button
   - Clear scribble history
   - About section

2. **`ui/settings/ConnectionInfoCard.kt`**
   - Partner name
   - Connection code
   - Connected since timestamp
   - Disconnect action

3. **`viewmodel/SettingsViewModel.kt`**
   - Manage connection state
   - Handle disconnect
   - Clear data

### Privacy Controls
- Disconnect and delete all data
- Clear scribble history
- Connection status visibility

---

## Phase 8: Testing & Polish

### Goal
Ensure reliability and polish the user experience.

### Testing Checklist

#### Unit Tests (Optional - Time permitting)
- Repository tests
- ViewModel tests
- Serialization tests

#### Manual Testing (Priority)
- [ ] Connection flow (create/join)
- [ ] Drawing experience (smooth strokes)
- [ ] Offline queue (airplane mode)
- [ ] Settings (disconnect, clear data)
- [ ] UI polish (animations, transitions)

### Polish Items
- Error messages (user-friendly)
- Loading states (connection, sync)
- Empty states (no scribbles)
- Accessibility (content descriptions)

---

## Simplified Implementation Strategy

Given the complexity of full system integration, we'll focus on:

### ✅ What We'll Implement
1. **Manual screensaver activation** (user navigates to tab)
2. **Background sync service** (when connected)
3. **Settings screen** (disconnect, clear data)
4. **Basic testing** (manual verification)
5. **UI polish** (error states, loading indicators)

### ⏭️ What We'll Skip (For Now)
1. Lock screen overlay (requires SYSTEM_ALERT_WINDOW)
2. Automatic idle detection (complex battery optimization)
3. Charging detection (not essential for MVP)
4. Comprehensive unit tests (time-intensive)

---

## Implementation Order

### Phase 6: System Integration (Simplified)
1. Create ScreensaverManager
2. Start/stop sync service on connect/disconnect
3. Add wake lock for drawing screen
4. Update Screensaver.kt to start sync service

### Phase 7: Settings & Privacy
1. Create SettingsViewModel
2. Create ConnectionInfoCard
3. Create ScreensaverSettingsScreen
4. Add settings navigation
5. Implement disconnect flow

### Phase 8: Testing & Polish
1. Manual testing checklist
2. Add error states
3. Add loading indicators
4. Polish animations
5. Add empty states
6. Final build and verification

---

## Estimated Time
- **Phase 6:** 30 minutes
- **Phase 7:** 45 minutes  
- **Phase 8:** 30 minutes  
**Total:** ~1.5 hours

---

## Success Criteria

✅ User can create/join connections  
✅ User can draw and send scribbles  
✅ Scribbles sync in background  
✅ User can view received scribbles  
✅ User can disconnect and clear data  
✅ App builds without errors  
✅ Basic manual testing passes  

---

**Ready to proceed with Phase 6?**
