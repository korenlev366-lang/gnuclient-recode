# OpenMiau Displace — design

**Date:** 2026-07-12  
**Status:** ready for user review  
**Ship path:** `gnuclient recode/`  
**Reference:** `OpenMiau/src/main/java/myau/module/modules/combat/Displace.java`  
**Approved:** Full port (**A**), approach **1** (single `DisplaceModule`)

## Problem

User wants OpenMiau **Displace**: while attacking a nearby player, alternate-tick yaw/strafe toward void (or a fixed offset) so knockback pushes them off — with optional blink and direction arrow.

## Goals

- Faithful OpenMiau Displace in GNUClient Forge recode.
- Reuse `BlinkManager` + existing `BlinkModules.DISPLACE`.
- Mode-tied settings via `visibleWhen`.
- HUD suffix: delay ms (OpenMiau `getSuffix`).

## Non-goals

- Raven / OpenMyau-Plus Displace variants.
- Mutual exclusion with KillAura (use rotation priority; document conflict).
- Separate void-scan library package (keep helpers private on the module unless file size forces a util).

## Settings

| Name | Type | Default | Visibility |
|------|------|---------|------------|
| Dynamic Angle | mode STATIC / DYNAMIC | STATIC | always |
| Yaw Offset | slider 0–180 | 90 | STATIC |
| Delay ms | slider 0–500 | 0 | always |
| Direction | mode LEFT / RIGHT | LEFT | STATIC |
| Show Direction | bool | true | always |
| Find Void | bool | false | STATIC |
| Blink | bool | false | always |
| Ignore Teammates | bool | true | always |
| Has Knockback | bool | false | always |

## Architecture

### `DisplaceModule`

Path: `gnu.client.module.modules.combat.DisplaceModule`

Port OpenMiau control flow and constants (`DISPLACE_WINDOW_TICKS`, void/dynamic scan constants, arrow geometry).

| OpenMiau hook | GNUClient |
|---------------|-----------|
| `UpdateEvent` PRE | Arm silent rotation + `fixMovement` equivalent on displace ticks (see Rotation below) |
| `StrafeEvent` | Hook via existing `ClientEventListener.onStrafe` → `DisplaceModule.patchStrafe` (same pattern as Velocity) |
| `PacketEvent` SEND C03 | `PacketListener.onSend` + `BlinkManager.setBlinkState(true, DISPLACE)`; release next living/tick |
| `LivingUpdateEvent` | Release blink if `releaseBlinkNextTick` |
| `Render3DEvent` | `RenderWorldLastEvent` via `ClientEventListener` or module `onRender` if already dispatched |

### Targeting

- Closest `EntityPlayer` within **9** blocks while attack key held.
- Skip self / dead / `deathTime != 0`.
- `Ignore Teammates` → `Utils.isTeammate` (or existing team helper).
- Bots: `RavenAntiBot.isBot` (OpenMiau `TeamUtil.isBot`).
- Friends: skip if friended API exists; else no-op (OpenMiau friends).

### Rotation / movement

- On displace tick: yaw = DYNAMIC void yaw **or** STATIC find-void yaw **or** fixed `rotationYaw ± yawOffset` (LEFT/RIGHT).
- OpenMiau `event.setRotation(yaw, pitch, 3)` → apply via `RotationState` / silent rotation with a dedicated Displace priority (recommend **2**: between KillAura `1` and Scaffold `3`) so KA can override when both fight for yaw; document in module javadoc.
- `MoveUtil.fixMovement(yaw)` → port as private helper or small util method matching OpenMiau’s ±1 remap + sneak 0.3 (distinct from `MoveFixUtil.fixStrafe` camera keys — Displace remaps after forcing forward).
- Strafe compensate next tick: `strafe = displaceLeft ? -1 : 1`.
- Off-tick after displace: `KeyBinding.onTick(attack)` when attack keycode ≠ 0 (OpenMiau).

### Blink

- When Blink on + active + displace tick + outbound C03: `BlinkManager.INSTANCE.setBlinkState(true, BlinkModules.DISPLACE)`, set `releaseBlinkNextTick`.
- Next tick: `setBlinkState(false, DISPLACE)`.
- On disable: force release if blinking module is DISPLACE.

### Void / collision

- Forge-safe: `World.getBlockState` + `addCollisionBoxesToList` / `isAirBlock` (same as OpenMiau). Allowed on this JVM (not Rain JNI ban).

### Arrow render

- Port OpenMiau GL arrow (body + head triangles) with fade (`ARROW_FADE_MS`).
- Gate on Show Direction; clear fade state when disabled / no target.

### Registration

- `GnuClientMod.safeRegister(new DisplaceModule())`.
- Wire strafe/render/living release through existing listeners if modules don’t already get those events.

## Testing / verification

1. Unit: settings defaults + `visibleWhen` for Yaw Offset / Direction / Find Void when DYNAMIC.
2. `./gradlew compileJava` / `build`.
3. In-game: STATIC left/right near void edge; Find Void; DYNAMIC; Blink on/off; arrow visible; Has Knockback gate.

## Risks

| Risk | Mitigation |
|------|------------|
| Rotation fight with KillAura | Priority 2; document |
| Blink fight with KA AUTO_BLOCK | Only blink when `BlinkModules.DISPLACE` owner; release promptly |
| `fixMovement` vs `fixStrafe` confusion | Keep Displace-local remap matching OpenMiau |

## Open items

- Exact `RotationState.applyState` call site (PreMotion vs PreUpdate) — match whichever path other silent-rot modules use for packet yaw in this codebase during implementation.
