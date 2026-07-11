# OpenMiau MoreKB — design

**Date:** 2026-07-11  
**Status:** ready for user review  
**Ship path:** `gnuclient recode/`  
**Reference:** `OpenMiau/src/main/java/myau/module/modules/combat/MoreKB.java`

## Problem

User wants OpenMiau **MoreKB** (10 sprint/KB modes) in GNUClient. Existing **W Tap** already covers a subset (Packet / SprintTap / Legit) from other sources.

## Goals

- New separate **MoreKB** module (Category.COMBAT), faithful OpenMiau port.
- Keep **W Tap** logic as-is.
- Mutual exclusion: enabling one disables the other.
- Mode-tied settings via `visibleWhen`.

## Non-goals

- Replacing or rewriting W Tap modes.
- Strategy-package split (single file is enough).
- OpenMyau-Plus MoreKB / SuperKnockback references.

## Decisions (approved)

1. Separate module (**A**).
2. Auto-disable sibling on enable (**B**).
3. Direct single-module port (**approach 1**).

## Modes (OpenMiau order / indices)

| Index | Name | Trigger sketch |
|-------|------|----------------|
| 0 | Legit | Tick + crosshair entity `hurtTime==10`: toggle sprint off/on |
| 1 | LegitFast | On attack target: clear `sprintingTicksLeft` when moving |
| 2 | LessPacket | hurtTime==10: stop sprint client + START_SPRINTING packet |
| 3 | Packet | hurtTime==10: STOP+START C0B |
| 4 | DoublePacket | hurtTime==10: STOP+START×2 |
| 5 | WTap | On attack: 2 ticks zero moveForward / stop sprint |
| 6 | SprintTap | On attack: 2-tick stop then restore sprint if forward |
| 7 | Silent | On C03 SEND during 2-tick window: inject STOP then START |
| 8 | SneakPacket | On attack: STOP_SPRINT + START_SNEAK + START_SPRINT + STOP_SNEAK |
| 9 | SpamS | On attack in range: force back key for N ticks |

Default mode: **Legit** (0), matching OpenMiau.

## Settings

- Mode (list above)
- Intelligent (bool) — angle gate for modes 0/2/3/4
- Only Ground (bool, default true)
- SpamS Distance, SpamS Tick — `visibleWhen` mode == SpamS

## Architecture

### `MoreKBModule`

- Path: `gnu.client.module.modules.combat.MoreKBModule`
- Implements needed hooks:
  - `noteForgeAttack` / `ClientEventListener` + `CombatAttackNotify` (same pattern as W Tap)
  - `onTick` for hurtTime / WTap / SprintTap / SpamS
  - `PacketListener.onSend` for Silent (C03)
  - `patchMovementInput` optional for WTap forward zeroing (prefer MovementInputHook like W Tap)
- Packets: `Mc.addToSendQueue` / `Mc.sendSprintActionPacket` / sneak action helpers
- KeyBinding back for SpamS; clear on disable
- Register in `GnuClientMod`

### Mutual exclusion

In `MoreKBModule.onEnable`: if W Tap enabled → `setEnabled(false)`.  
In `WTapModule.onEnable`: if MoreKB enabled → `setEnabled(false)`.

Same pattern as Blink/Lagrange conflict helper.

### Sprint interaction

SpamS / WTap may need `SprintModule.shouldSuppressSprintKey`-style cooperation only if sprint fights MoreKB; prefer OpenMiau behavior first (direct `setSprinting` / movementInput). Revisit only if Sprint module blocks MoreKB in practice.

## Verification

1. Compile; module appears in Combat category.
2. Enable MoreKB → W Tap turns off; enable W Tap → MoreKB turns off.
3. Mode SpamS shows distance/tick settings only.
4. Smoke: Legit/Packet don’t crash; Silent cancels nothing but injects C0B around C03; disable clears back key.

## Risks

- Overlap with W Tap if exclusion fails.
- Silent mode injects on every C03 while ticks active — match OpenMiau exactly.
- SpamS forces S key — must reset on disable / world change.
