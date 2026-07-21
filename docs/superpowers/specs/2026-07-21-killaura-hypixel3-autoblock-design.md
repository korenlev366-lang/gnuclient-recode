# KillAura HYPIXEL3 Autoblock — design

**Date:** 2026-07-21  
**Status:** approved  
**Ship path:** `gnuclient recode/`  
**Reference:** `wsamiaw/.../KillAura.java` auto-block mode `HYPIXEL3` (index 11)

## Problem

gnuclient KillAura autoblock ends at GRIM. User wants wsamiaw **HYPIXEL3** (Cryptix-style 3-tick blink block cycle) plus **Disable Keep Sprint On KB**.

## Goals

- Add Auto-block mode **HYPIXEL3**.
- Port the 3-tick `hypixel3Asw` cycle and AUTO_BLOCK blink behavior.
- Add **Disable Keep Sprint On KB** (default on, visible for HYPIXEL3).
- On self S12 / non-zero explosion while mode + setting: `player.setSprinting(false)`.

## Non-goals

- Porting WATCHDOG2.
- Changing other autoblock modes.
- Matching wsamiaw mode index 11 (gnu appends as next index after GRIM).

## Behavior

While valid target in AB range and not digging/placing:

| `hypixel3Asw` | Action |
|---------------|--------|
| 0 | stop block if blocking; `attack=false`; → 1 |
| 1 | stop block if blocking; `attack=false`; → 2 |
| 2 | if not blocking → `swap=true`; `blockedBlinkPulse=true`; → 0 |

- Blink AUTO_BLOCK **on** while has valid target; **off** when no target (reset asw).
- `isBlocking=true`, `fakeBlockState=true` while targeting.
- After attack: existing `applyAfterAttack` handles swap + blink pulse.

## Files

| File | Change |
|------|--------|
| `KillAuraAutoBlock.java` | `HYPIXEL3` const, state, case, reset |
| `KillAuraModule.java` | mode name, setting + visibility, PacketListener KB sprint |

## Verification

- `./gradlew build`
- Mode appears in ClickGUI; KB setting only for HYPIXEL3
- Cycle advances; no target resets blink/asw
