# Sweet Corner: Village Mode Implementation Plan

This document outlines the architecture and implementation strategy for the new **Village Mode**, an interactive, animated alternative to the current Galaxy View. 

## Goal Description
The objective is to create a dynamic, fully animated "Village Mode" where the user and their connections are represented by their Twigi characters living in a shared environment. Instead of planets, users have Castles (or houses), and Twigis freely roam the village. When a message is received, the corresponding Twigi runs to the screen to deliver it via a chat bubble.

> [!NOTE]
> This feature introduces a tile-based coordinate system for castles and props, and a robust wandering AI for the Twigi characters.

## Proposed Architecture

### 1. Mode Switcher
We will add a sleek UI toggle in the `Developer.kt` (or main dashboard) to let the user switch between `Galaxy` and `Village` visual modes. This preference will be persisted in `SharedPreferences`.

### 2. 2.5D Isometric HD-2D Environment
To achieve the "full 3D type" feel while maintaining maximum performance and compatibility with our existing beautifully animated Twigi character sprites, we will build a **2.5D Isometric Engine** using Compose `Canvas`.
- **Why 2.5D?** It allows us to use our existing high-quality Twigi animations, depth-sorts perfectly (characters behind castles are occluded), and provides that nostalgic yet modern "Animal Crossing / Stardew Valley" vibe with glassmorphic UI overlays.
- A virtual grid will map out the village ground, utilizing pan/zoom gestures identical to the Galaxy View.

### 3. Castles and Domain Customization
- **Placement**: Your castle is at the center `(0, 0)`. When a connection is created, their castle is assigned an adjacent coordinate.
- **Data Model**: We will create a `VillageDatabase` or extend `ScreensaverDatabase` to store:
  - `ItemType` (Castle, Tree, Lamp, etc.)
  - `OwnerId` (Connection ID)
  - `GridX, GridY`
- **Dragging**: Long-pressing a castle or prop will lift it up, allowing you to drag it across the isometric grid and drop it in a new location.

### 4. Twigi Roaming AI
- Each Twigi character spawns at their respective castle.
- A continuous background `LaunchedEffect` loop will assign them random nearby waypoints.
- The `Twigi` renderer will face the correct direction (Left/Right/Up/Down) based on their velocity vector.
- The animation sprite will switch from `Idle` to `Walk` based on movement.

### 5. Interactive Chat Delivery (The "Messenger" System)
When an incoming chat message is received from a connection:
- The wandering AI for that connection's Twigi is temporarily overridden.
- The Twigi calculates a path directly toward the user's viewport (or center castle).
- Upon arrival, a smooth, bouncy **Chat Bubble** pops up over the Twigi.
- The text is revealed using a typing animation.
- **Action**: Tapping the Twigi or the bubble opens the direct chat window with that connection.

---

## Open Questions & Design Decisions

> [!IMPORTANT]
> **Please review these questions before we begin execution!**

1. **Perspective preference**: Are you happy with a 2.5D isometric view (like classic RPGs or Habbo Hotel), which works perfectly with the Twigi sprites we already have? Or were you envisioning a true 3D environment using 3D models (which would require a completely new renderer and 3D asset generation)? I highly recommend the 2.5D isometric approach for maximum charm and performance!
2. **Prop acquisition**: Should props (fences, flowers) be something the user just spawns from a menu for free, or is there a "currency/unlock" system planned for later? (For now, I can just build a free placement menu).
3. **Village size**: Should the village be an infinite scrolling plane, or a fixed size island floating in space/clouds?

## Verification Plan

### Automated / Logic Tests
- Ensure coordinate saving and loading works accurately.
- Verify Twigi pathfinding doesn't let them walk through castles or props (collision detection).

### Manual Verification
- Test mode switcher toggle (Galaxy <-> Village).
- Drag and drop a castle, restart app, verify it stayed in place.
- Send a test message and watch the Twigi run to the screen and pop the chat bubble.
- Verify 60FPS smooth animations during panning and zooming.
