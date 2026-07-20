# ESP Glow Outline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Soft fill + multi-pass line bloom on player ESP and network ghost boxes (Backtrack / Lagrange / Blink); ItemESP / BedESP stay fill-only.

**Architecture:** Add `RenderHelper.drawGlowingBoundingBox` (multi-pass `GL_LINES`), expose via `EspDraw.fillWithGlow` / `fillBatchedWithGlow`, wire EspModule + network modules through those helpers. No new settings; glow RGB = fill RGB.

**Tech Stack:** Forge 1.8.9, LWJGL GL11, existing `EspDraw` / `RenderHelper` / module `onRender` paths.

**Spec:** `docs/superpowers/specs/2026-07-20-esp-glow-outline-design.md`

---

## File map

| File | Responsibility |
|------|----------------|
| `util/RenderHelper.java` | `drawGlowingBoundingBox` primitive |
| `util/EspDraw.java` | `fillWithGlow` / `fillBatchedWithGlow` |
| `visual/EspModule.java` | Player boxes → glow helper (incl. display-list path) |
| `network/BacktrackModule.java` | Ghost fill → `fillWithGlow` |
| `network/LagrangeModule.java` | `drawGhostBox` → `fillWithGlow` |
| `network/BlinkModule.java` | Replace raw `drawBoundingBox` with `fillWithGlow` |

---

### Task 1: Glow primitives in RenderHelper + EspDraw

**Files:**
- Modify: `src/main/java/gnu/client/util/RenderHelper.java`
- Modify: `src/main/java/gnu/client/util/EspDraw.java`

- [ ] **Step 1: Add `drawGlowingBoundingBox`**

Reuse the 12-edge vertex pattern from `drawBoundingBox`. Draw ~4 passes outer→inner, e.g. widths `{8, 5, 3, 1.5}` and alphas `{0.08, 0.16, 0.35, 0.9}` times caller RGB. Document: must be between `begin()`/`end()`.

- [ ] **Step 2: Add EspDraw glow wrappers**

```java
public static void fillWithGlow(..., float r,g,b, float alpha) {
    RenderHelper.drawGlowingBoundingBox(..., r, g, b, /*core*/);
    RenderHelper.drawFilledBox(..., r, g, b, resolveAlpha(alpha));
}
public static void fillBatchedWithGlow(float[] boxes, int boxCount, float r,g,b, float alpha) {
    // for each box: glow then fill (or glow all then fill all — pick consistent order)
}
```

Update class javadoc: plain `fill` remains for Item/Bed; glow APIs for players/network.

- [ ] **Step 3: Compile**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew compileJava --no-daemon
```

Expected: SUCCESS.

---

### Task 2: Wire EspModule (player ESP)

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/visual/EspModule.java`

- [ ] **Step 1: Batched path**

Replace `EspDraw.fillBatched(...)` with `EspDraw.fillBatchedWithGlow(...)`.

- [ ] **Step 2: Display-list path**

Where `RenderHelper.drawFilledBoxList` is used after `glColor4f(..., DEFAULT_FILL_ALPHA)`, also draw glow for that box AABB in world space (same extents as the list box). Do not leave glow skipped when Performance display lists are on.

- [ ] **Step 3: Compile + quick in-game check note**

EspModule on → soft fill + visible glow in module RGB.

---

### Task 3: Wire network ghost boxes

**Files:**
- Modify: `BacktrackModule.java` (EspDraw.fill → fillWithGlow)
- Modify: `LagrangeModule.java` (`drawGhostBox` → fillWithGlow)
- Modify: `BlinkModule.java` (drawBoundingBox → fillWithGlow with default fill alpha)

- [ ] **Step 1: Backtrack + Lagrange**

Swap fill call sites to `fillWithGlow` with existing RGB/alpha args.

- [ ] **Step 2: Blink**

Replace outline-only box with `fillWithGlow` (soft fill + glow). Keep existing color.

- [ ] **Step 3: Confirm Item/Bed untouched**

Grep: ItemEspModule / BedEspModule still call `fill` / `fillBatched` only.

---

### Task 4: Verify build

**Files:** none (build only)

- [ ] **Step 1: Build**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew compileJava remapJar --no-daemon
```

Expected: SUCCESS; jar in `build/libs/`.

- [ ] **Step 2: In-game checklist (user)**

- EspModule: fill + glow  
- Backtrack / Lagrange / Blink ghosts: glow  
- ItemESP / BedESP: fill only  

Do **not** commit unless the user asks.
