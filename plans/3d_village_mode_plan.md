# 🏰 3D Village World: Full Technical & Architectural Plan

This document outlines the detailed architecture, data schemas, rendering engine, and feature breakdown for **Full 3D Village Mode** in Gigi.

---

## 🌟 Executive Summary

**Village Mode** is a full 3D interactive world alternative to Galaxy View. Instead of 2D planets orbiting in space, users and their connected partners inhabit a real 3D environment.
- **3D VRM Avatars**: Uses full 3D VRM models (built via CharacterStudio) with physics, spring bones, expressions, and animations.
- **Expanding 3D Ground Plane**: As new connections join, the 3D terrain dynamically expands outward with connecting stone pathways.
- **Remote Castle Customization**: Each user customizes their own house/castle style, which is synced to all connected partners so their castle reflects their personal style in everyone's village.
- **Free & Premium Props**: Interactive 3D prop placement (trees, fences, glowing fountains, seasonal items).
- **Interactive 3D Messenger**: When a message arrives, the partner's 3D VRM character runs directly toward the screen to deliver the message via a 3D speech bubble.

---

## 🏗️ Technical Architecture

```
                               ┌──────────────────────────────────────────────┐
                               │            Android App (Compose)             │
                               │  Village3DView.kt / ScreensaverSettingsScreen│
                               └──────────────────────┬───────────────────────┘
                                                      │
                                                      ▼
                               ┌──────────────────────────────────────────────┐
                               │  WebView 3D Engine (assets/vrm_village.html) │
                               │  Three.js r177 + @pixiv/three-vrm v3         │
                               └──────┬───────────────────────────────┬───────┘
                                      │                               │
                                      ▼                               ▼
                      ┌───────────────────────────────┐   ┌───────────────────────────────┐
                      │ 3D VRM Avatar Manager         │   │ Dynamic 3D Ground & Castles   │
                      │ - Local VrmCacheManager       │   │ - Expanding terrain grid      │
                      │ - Idle, Walk, Wave animations │   │ - Custom Remote Castle skins  │
                      └───────────────────────────────┘   └───────────────────────────────┘
```

---

## 📐 1. 3D WebGL Engine & Canvas Setup

* **File Location**: `app/src/main/assets/vrm_village.html`
* **Renderer**: Three.js WebGLRenderer with PCFSoftShadowMap, ambient lighting, directional sun/moon light with shadow casting.
* **Camera System**:
  - `PerspectiveCamera(45, aspect, 0.1, 100)` with `OrbitControls`.
  - Smooth camera transitions when selecting a partner's castle or tapping a Twigi character.
* **Performance Optimizations**:
  - Frustum culling for distant props.
  - Geometry instancing for repetitive props (fences, grass patches, trees).
  - Uses `VrmCacheManager` (`file:///android_asset/` and `file:///data/user/0/...`) so VRM models are loaded instantly from local device storage without network latency.

---

## 🟩 2. Expanding 3D Ground & Sector System

1. **Origin Hub `(0, 0, 0)`**:
   - The user's own Castle is positioned at the center of a primary circular 3D meadow tile.
2. **Radial/Grid Extension Algorithm**:
   - When `connections.size > 0`, the engine calculates polar coordinates `(radius, angle)` for each connection:
     $$\text{angle}_i = \frac{2\pi \cdot i}{N}, \quad \text{radius} = 12.0 \text{ meters}$$
   - A new ground tile mesh is generated at `(\text{radius} \cdot \cos(\text{angle}), 0, \text{radius} \cdot \sin(\text{angle}))`.
   - A 3D cobblestone pathway mesh connects the center hub to each partner's tile.

---

## 🏰 3. Remote Castle Customization & Sync

### Data Schema
Each member identity persists a `villageConfigJson` object in `ClientIdentityStore` and syncs over server WebSocket/REST:

```json
{
  "castleStyle": "FAIRY_COTTAGE",
  "roofColor": "#7C3AED",
  "wallColor": "#F4EEFF",
  "bannerEmoji": "🌸",
  "vrmUrl": "https://gigi.iamanraj.com/avatars/user.vrm",
  "placedProps": [
    { "type": "CHERRY_TREE", "x": 2.5, "z": -1.2, "rotY": 45, "isPremium": true },
    { "type": "STONE_BENCH", "x": -1.8, "z": 0.5, "rotY": 90, "isPremium": false }
  ]
}
```

### Castle Styles Available
- **Default**: Classic Stone Castle 🏰
- **Fairy Cottage**: Cozy wooden cabin with ivy and glowing windows 🍄
- **Gothic Spire**: Dark violet stone tower with glowing crystals 🔮
- **Golden Palace**: Shimmering marble with gold trim 🌟
- **Neon Cyber**: Futuristic neon-lit pavilion ⚡

---

## 🎨 4. Free & Premium Props Marketplace

### Prop Categories
| Category | Free Props (Default) | Premium Props (Unlockable) |
|---|---|---|
| **Nature** | Oak Tree, Flower Bed, Grass Patch | Cherry Blossom Tree, Magic Mushroom, Autumn Maple |
| **Structures** | Wooden Fence, Stone Bench, Pathway | Fairy Fountain, Bonfire with Fireflies, Crystal Obelisk |
| **Lighting** | Classic Lantern, Torch | Neon Archway, Floating Lanterns, Aurora Borealis Sky |

### Placement Mode (Raycasting)
- Users tap **Decorate 🎨** to enter editing mode.
- Touches are mapped via Three.js `Raycaster` to grid coordinates.
- Controls: **Place**, **Rotate 90°**, **Delete**, **Lock Position**.

---

## 📬 5. Live 3D Messenger Delivery

When a real-time message or scribble is received from a partner:
1. **Event Trigger**: `ConnectionBootstrapManager` receives message event.
2. **Bridge Invocation**: Calls `window.villageEngine.deliverMessage(partnerId, messageText)`.
3. **Behavior**:
   - Partner's 3D VRM model pauses wandering AI.
   - Calculates a linear path along the cobblestone road toward camera position `(0, 1.2, 1.5)`.
   - Plays `running` animation.
   - Plays `waving` gesture and triggers facial expression (`happy` / `smile`).
   - Pops a animated 3D HTML/CSS speech bubble over their head with typing text effect.
   - Tapping the Twigi or bubble opens direct chat screen.

---

## 📋 6. Verification & Testing Checklist

- [ ] **3D Rendering Benchmark**: Maintain 60 FPS on mid-range Android devices with up to 5 simultaneous VRM models.
- [ ] **Offline Loading**: Verify VRM models load from `VrmCacheManager` without internet.
- [ ] **Remote Castle Sync**: Verify editing castle style on Device A updates Castle skin on Device B in real time.
- [ ] **Grid Expansion**: Test ground extension smoothly from 1 to 10 connections.
- [ ] **Touch Dragging**: Verify 3D prop placement raycasting aligns accurately on touch screen.
