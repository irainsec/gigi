# Gigi Project Memory - 2026-04-25

## Current Status
We are in the middle of stabilizing the "Parental Control" features: **Remote Photo**, **Live Location**, and **Live Video**.

### Recent Successes
1.  **Server Connection**: Fixed "Server Offline" by reverting to binary-only heartbeats. The JSON heartbeats were likely overwhelming the server or causing protocol errors.
2.  **Live Location**: User confirmed it is working, thoughJoinee logs showed some system GPS disabled issues earlier.
3.  **Command Delivery**: Implemented "Broadcast Fallback" (`recipientDeviceId = null`) for all `remote_command` actions. This ensures commands reach the partner even if their Device ID has changed or is stale in our DB.
4.  **Photo Capture**: Fixed a logic error where `isSent` was set to `true` immediately after capture, which prevented the `syncLoop` from ever uploading the image.

## Open Issues
1.  **Remote Photo Upload**: Even though capture is working, the sync seems to be failing or "looping". User reports "looping sending to partner".
2.  **ID Mismatch ("null is offline")**: The Creator phone sometimes shows "null is offline", indicating the `partnerDeviceId` in the `Connection` table is NULL.
    *   This prevents targeted `scribble` messages from working (remote commands use broadcast, so they work, but scribbles/photos need a target).
3.  **Background Persistence**: When the app is swiped away, we need to ensure the `ScreensaverSyncService` keeps the camera and location features alive.

## Technical Details for Next Chat
*   **Target File**: `app/src/main/java/com/aman/gigi/data/sync/ScribbleSyncManager.kt`
*   **Key Logic to Fix**:
    1.  **Handshake ID Exchange**: In `onWebSocketConnected`, we should immediately send a `PRESENCE_SNAPSHOT` that includes our `deviceId`.
    2.  **Scribble Target Fallback**: If `partnerDeviceId` is null in `sendScribble`, we should either:
        *   Trigger a `PRESENCE_SNAPSHOT` request immediately.
        *   Try broadcasting the scribble (risky for data usage, but works).
    3.  **Sync Loop Logging**: Add more `Log.e` in `processOutboundAction` to see EXACTLY why the scribble send is failing (e.g., is the server returning an error?).

## User Information
*   **Joinee ID**: `00055342T000050`
*   **Creator ID**: `10BFBH145T0012D`
*   **Server**: `64.227.143.149:8080` (Standard WebSocket)
