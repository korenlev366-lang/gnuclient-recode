# BridgeAssist — raven-bS parity design

**Date:** 2026-07-12  
**Status:** ready for user review  
**Ship path:** `gnuclient recode/`  
**Reference:** `raven-bS/src/main/java/keystrokesmod/module/impl/player/BridgeAssist.java`  
**Approved approach:** **A** — full raven port (input-time sneak + SimulatedPlayer + collision edge + ClientRotation pre-place)

## Problem

Current `BridgeAssistModule` claims raven parity but diverges in ways that change bridging feel:

| Area | raven-bS | Current recode |
|------|----------|----------------|
| Decision timing | `PrePlayerInputEvent` (before move) | Mostly `onTick` + static `forceSneak` next frame |
| Prediction | `SimulatedPlayer` one tick, `sneak=false` | Approximate motion AABB shift |
| Edge metric | `getCollidingBoundingBoxes` under predicted feet | `isAirBlock` grid |
| Air / jump / unsneak | Full raven state machine | Early `!onGround` clear; jump path differs |
| Manual sneak | Early-out / repress when “Sneak key pressed” | Incomplete |
| Pre-place | `ClientRotationEvent` + yaw/pitch `smoothRotation` | Mutates client pitch on tick |

A prior vault note (`Decision - BridgeAssist predictive edge detection`) papered over timing with approximate prediction while avoiding collision — that was driven by Rain JNI SIGSEGV rules, not Forge constraints.

## Goals

- Behavior match raven BridgeAssist for sneaking, edge offset, unsneak delay, sneak-on-jump, condition gates, and optional pre-place.
- Use existing Forge hooks: `PrePlayerInputEvent`, `ClientRotationEvent`, `PacketEvents` / C08.
- Safe on Forge JVM: `World.getCollidingBoundingBoxes` is allowed (already used by Scaffold / Velocity).

## Non-goals

- Scaffold Eagle / Ledge / SafeWalk changes.
- RainClient Eagle / native BridgeAssist.
- Porting raven’s full script API surface beyond what BridgeAssist needs.
- Item whitelist setting (raven has it) — **defer** unless requested after core parity.
- Changing ClickGUI grouping infrastructure (flat settings OK if labels match).

## Decisions (approved)

1. **Approach A** — line-faithful control flow + real sim + collision edge.
2. **Supersede** approximate predictive AABB / `isAirBlock` edge for this module.
3. **Remove** BA dependence on tick-driven `forceSneak` / `suppressSneak` for correctness (hook may keep no-op stubs so `MovementInputHook` does not break).

## Settings (raven names / defaults)

| Setting | Type | Default | Notes |
|---------|------|---------|-------|
| Pre place | bool | false | ClientRotation aiming |
| Edge offset | slider blocks | 0, 0–0.3, step 0.01 | Sneak when predicted offset &gt; value |
| Unsneak delay | slider ms | 50, 50–300, step 5 | Hold sneak after safe before release |
| Sneak on jump | slider ms | 0, 0–500, step 5 | 0 = off |
| Sneak key pressed | bool | false | Require bind; repress / force-release semantics |
| Holding blocks | bool | false | Held item must be `ItemBlock` |
| Looking down | bool | false | Pitch ≥ 70 |
| Not moving forward | bool | false | Fail if `forward &gt; 0` |

Info string: edge offset (raven `getInfo`).

## Architecture

### 1. `BridgeAssistModule` rewrite

**Path:** `gnu.client.module.modules.player.BridgeAssistModule`

- Register Forge listeners while enabled (or always subscribe and gate on `isEnabled()` — match other modules).
- **Primary:** `@SubscribeEvent` `PrePlayerInputEvent` — port raven `onPrePlayerInput` almost 1:1:
  - Gates: null check, `currentScreen`, flying.
  - Manual sneak vs `sneakKeyPressed`.
  - Condition clears: not-forward / looking-down / holding-blocks.
  - Jump + `sneakOnJump` arm.
  - Sim predict → `computeEdgeOffset` → press / tryRelease.
- **Pre-place:** `@SubscribeEvent` `ClientRotationEvent` — port raven `onClientRotation` (blocks held, pitch scan 60–90, side faces only, `RotationUtils.smoothRotation` 15 / 20f). Skip if BedAura-style mouse override exists and is active (only if recode has an equivalent; else no-op check).
- **Packets:** on C08 block place (direction ≠ 255) set `placed` when sneaking-from-module + sneak-key mode (raven).
- **Disable:** clear sneak flags / unsneak timers (raven `onDisable`).
- Stop using `onTick` for edge decisions. Do not call `Mc.setSneaking` as the primary actuation — set `PrePlayerInputEvent.setSneak` (mixin already applies event → `MovementInput`).

### 2. Simulated player

**Need:** raven snippet only:

```text
SimulatedPlayer.fromClientPlayer(movementInput)
movementInput.sneak = false
sim.tick()
sim.getEntityBoundingBox()
```

**Plan:** Add `gnu.client.utility.sim.SimulatedPlayer` by porting raven `keystrokesmod.script.model.SimulatedPlayer`, stripping or stubbing raven-only deps (`ModuleManager` / `NoSlow` → no-op or GNUClient equivalents if present). Prefer a complete tick port over a half-baked lite sim so edge offsets match.

Do **not** reuse `ScaffoldSimulatedPlayer` (LiquidBounce-shaped; different motion / edge API).

### 3. Edge offset

Port raven `computeEdgeOffset(AxisAlignedBB simBox)`:

- Ground check AABB: `minY - 0.01` … `minY`
- `world.getCollidingBoundingBoxes(player, groundCheck)`
- Empty → `NaN` (over edge / no ground)
- Else chebyshev distance from feet center to closest ground box

### 4. MovementInputHook

- `BridgeAssistModule.patchMovementInput` becomes a no-op (or removed from hook if safe).
- Sneak already applied in mixin from `PrePlayerInputEvent` **before** `MovementInputHook`; BA must run via EVENT_BUS on that event, not only the static patch.

**Ordering note:** Forge `PrePlayerInputEvent` fires inside the mixin overwrite **before** sneak slowdown multiply. Raven does the same. Keep BA on that event, not on `MovementInputHook` after multiply.

### 5. Vault / docs

- New decision: `gnu client dev/Decision - BridgeAssist raven parity.md` (same vault as existing BridgeAssist note).
- Mark `Decision - BridgeAssist predictive edge detection` **superseded**, link both ways.
- Update Architecture / module note if present.

## Control flow (sneak) — raven reference

```
onPrePlayerInput:
  if screen / flying → return
  if manualSneak && !requireSneak → resetUnsneak; return
  if requireSneak && (!manual || no move) → repress / return
  if condition fails → clearSneak; return
  if jump onGround moving && sneakOnJump > 0 → arm jump delay; pressSneak; return
  sim tick without sneak
  if offset NaN → press on ground / tryRelease in air (raven branch)
  else if offset > edgeOffset → press else tryRelease
```

Unsneak delay / jump hold: tick counts from `player.ticksExisted` (not a module-local tick counter).

## Error handling / edge cases

- GUI open / flying: no assist; leave input alone (except disable cleanup).
- S08 / teleport: N/A for BA (not a lag module).
- Scaffold enabled simultaneously: both may set sneak on the same event — document “last writer wins by bus order”; no mutual exclusion unless user requests later.
- Collision on weird blocks (fences, slabs): raven uses colliding boxes — keep that.

## Testing / verification

1. `./gradlew compileJava` / `build` in `gnuclient recode/`.
2. In-game 1-wide bridge, edge offset `0.10`, `0.20`, `0.30` — should not walk off like old approximate path; should feel like raven.
3. Unsneak delay: release only after delay when stepping back to center.
4. Sneak on jump &gt; 0: holds sneak across jump window.
5. Conditions off: assist without looking down / holding blocks.
6. Pre place on: pitch eases toward placeable side faces while holding blocks.
7. “Sneak key pressed” on: force-release / repress after place (raven).

## Risks

| Risk | Mitigation |
|------|------------|
| SimulatedPlayer port large / deps | Stub NoSlow; keep physics core intact |
| EVENT_BUS order vs other input modules | Subscribe explicitly; verify vs Scaffold/Velocity JumpReset |
| Vault path split (`gnu client` vs `gnu client dev`) | Update the note that already documents BridgeAssist |

## Open items (non-blocking)

- Item whitelist (raven) — deferred.
- BedAura `shouldOverrideMouseOver` equivalent — skip if absent.
