# OpenMiau Velocity modes — design

**Date:** 2026-07-11  
**Status:** ready for user review  
**Ship path:** `gnuclient recode/`  
**Reference:** `OpenMiau/src/main/java/myau/module/modules/combat/Velocity.java` + `velocity/*`

## Problem

GNUClient Velocity only has Reduce / JumpReset / Intave. User wants **all OpenMiau Velocity modes** skidded into GNUClient, replacing the current mode set.

## Goals

- Replace mode list with OpenMiau’s 26 modes (exact names / order from OpenMiau `ModeProperty`).
- Default mode: **Standard**.
- Structure: OpenMiau-style `VelocityMode` strategy package under GNUClient.
- Mode-tied settings via existing `visibleWhen`.
- Faithful behavior ports; adapt events to GNUClient hooks.

## Non-goals

- Porting OpenMyau-Plus Velocity (wrong tree).
- Full OpenMiau `DelayManager` / LongJump modules.
- Changing unrelated combat modules (KillAura lag policy, etc.).
- Keeping GNUClient Reduce / JumpReset / Intave as separate mode names.

## Decisions (approved)

1. **Replace** current modes with OpenMiau list only (option A).
2. Default **Standard** (option A).
3. Architecture: mirror OpenMiau `VelocityMode` package (approach 1).

## Mode list (OpenMiau order)

OMDelay, Reverse, LegitTest, LegitSmart, IntaveReduce, Grimtest, JumpReset, Standard, AAC, Bounce, BufferAbuse, Delay, Grim, GrimReduce, Ground, Intave, Karhu, Legit, MMC, Matrix, Redesky, Tick, UniversoCraft, Vulcan, WatchdogPrediction, Watchdog.

Default index = index of **Standard** in that list.

## Architecture

### Package

`gnu.client.module.modules.combat.velocity/`

- `VelocityMode.java` — abstract hooks
- One `*Velocity.java` per mode (ported from OpenMiau)
- Optional: `VelocityDelayQueue.java` for OMDelay S12 hold/release
- Optional: tiny `MoveUtil`-equivalent helpers if not already in `Mc`

### `VelocityModule`

- Holds shared settings + shared mutable state (flags/counters matching OpenMiau `Velocity` fields used by modes).
- Registers modes, `getActiveMode()`, dispatches enable/disable/tick/packet/attack.
- Implements `PacketListener`; may register attack hook via `ClientEventListener` or module API.
- `reducesKnockback()` updated for new modes (true when enabled and mode actually reduces).

### Hook mapping

| OpenMiau | GNUClient |
|----------|-----------|
| Packet RECEIVE/SEND | `PacketListener.onReceive` / `onSend` (cancel semantics per existing PacketEvents) |
| Update PRE/POST | Tick / living-update bridges as needed per mode |
| Attack | Forge `AttackEntityEvent` note → `VelocityModule.noteAttack` |
| Knockback event | Prefer S12 mutation via `IAccessorS12PacketEntityVelocity`; add post-apply hook only if a mode requires it |
| MoveInput / Strafe / Jump | Thin mixin or existing movement hooks only where a mode uses them |
| `Myau.delayManager` | Local S12 delay queue owned by Velocity |
| Missing LongJump | Treat as not enabled |

### Settings to port

From OpenMiau `Velocity` (with `visibleWhen` by mode name):

- delay-ticks, delay-chance (OMDelay)
- legit-smart-jump-limit (LegitSmart)
- intave-reduce-factor, intave-reduce-hurt-time (IntaveReduce)
- chance (Legit / LegitTest / LegitSmart)
- horizontal, vertical (Standard / BufferAbuse / Redesky / Vulcan)
- explosions-horizontal, explosions-vertical (Standard)
- grim-reduce-jump-limit (Grimtest)
- fake-check, debug-log, on-swing (global as in OpenMiau)

Use GNUClient `SliderSetting` / `BoolSetting` / `ModeSetting`; percentages as 0–100 sliders where OpenMiau uses `PercentProperty`.

### Accessors

- Reuse: `IAccessorS12PacketEntityVelocity`, `Mc.setTimerSpeed` / timer accessors.
- Add only if required by a mode: web flag, `speedInAir` on player.

## Config compatibility

Saved configs with old mode indices (0=Reduce, 1=JumpReset, 2=Intave) will select wrong modes after update. Acceptable; document in release notes. No automatic migration required.

## Verification

1. Compile + unit smoke: mode list length 26, default is Standard, `visibleWhen` hides Standard-only settings on JumpReset.
2. Manual: Standard H/V scales S12; JumpReset jumps on hurt; disable restores timer/speedInAir if a mode changed them.
3. OMDelay: S12 held then released after delay ticks without requiring global DelayManager.
4. No lag-module policy regressions (Velocity must not break Blink/Lagrange packet ownership rules — use `Mc.addToSendQueue` / existing PacketUtil patterns as appropriate).

## Risks

- Some modes depend on OpenMiau-only utilities/events — must be adapted carefully, not left as broken stubs.
- Watchdog/Vulcan/Delay modes that queue packets need clear ownership vs Blink/Lagrange (do not steal queues; keep Velocity-local buffers).
- Large file count — implement via subagent-driven plan with mode batches.
