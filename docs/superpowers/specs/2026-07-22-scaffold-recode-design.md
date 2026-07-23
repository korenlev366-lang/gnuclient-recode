# Scaffold recode (v1) — design

**Date:** 2026-07-22  
**Status:** approved  
**Ship path:** `gnuclient recode/`  
**Approach:** Mirror KillAura silent rotations + MoveFix wiring (priority 3); thin new Scaffold module (do not restore deleted LiquidBounce tree).

## Problem

The previous Scaffold was removed for a clean recode. v1 needs a simple, Grim-friendly bridge scaffold with KA-style silent aim/MoveFix, item spoof, a few aim modes, KeepY Off/Telly, and vanilla jump tower.

## Goals

- Silent rotations + Silent MoveFix using the same runtime contract as KillAura (`PlayerUpdateHook.requestRotation`, `RotationState`, `MoveFixUtil` / `MoveFixHook` at `SCAFFOLD_MOVE_FIX_PRIORITY = 3`).
- GCD-quantized rotations visible in F5 / FreeLook.
- Rotation speed: two sliders **min** / **max** (1–100, default **60** / **80**); each tick sample a random speed in `[min, max]` and apply to yaw and pitch stepping (same range for both axes).
- Aim modes: **Backwards**, **GodBridge**, **Nearest**, **Sideways**.
- KeepY: **Off** | **Telly**.
- Hotbar: always **item spoof** (server places on biggest block stack; client hand/hotbar stay on the visual slot). Skip C09 when the place slot is already selected.
- Tower: hold jump → place under feet with current aim mode; vanilla jump only (no motion/timer/pulldown tower). Works in both KeepY modes.

## Non-goals (v1)

- Expand / Breezily / technique tree from old Scaffold.
- Blink, sprint modes, motion towers, Strict/Off MoveFix settings.
- Separate horizontal vs vertical speed sliders.
- Visual Switch mode (always spoof only).
- Refactoring KillAura into a shared controller (mirror pattern only).

## Settings (ClickGUI)

| Setting | Type | Default | Notes |
|---------|------|---------|--------|
| Aim | Mode | Backwards | Backwards / GodBridge / Nearest / Sideways |
| KeepY | Mode | Off | Off / Telly |
| Rotation min | Slider 1–100 | 60 | Lower bound of per-tick random speed |
| Rotation max | Slider 1–100 | 80 | Upper bound; clamp so max ≥ min at use time |

No Rotations mode, no Move-fix setting: while enabled, Scaffold always uses silent rotations + Silent MoveFix.

## Architecture

```
ScaffoldModule (PLAYER)
  ├─ block select + spoof slot state
  ├─ target find (under feet / edge / tower under)
  ├─ aim mode → target yaw/pitch
  ├─ KeepY/Telly + tower jump input
  └─ silent step + place (KA-style hooks)

Runtime (existing)
  PlayerUpdateHook.onPreUpdate / beforeWalking → Scaffold hooks
  MovementInputHook → MoveFix remap + Telly jump
  RotationState priority 3 + MoveFixHook
  MixinGuiIngame (restore) → spoof hotbar current item for render
```

**Priority:** When both KA and Scaffold claim MoveFix, existing priority numbers apply (KA=1, Displace=2, Scaffold=3). Scaffold should only arm priority 3 while it owns an active place look; do not clear KA’s state incorrectly (same ownership rule KA already documents).

## Behavior

### Enable / disable

- On enable: remember current hotbar slot as **visual spoof slot**.
- On disable: flush any pending place look; clear `RotationState` if Scaffold owns it; restore real `currentItem` to visual slot if different (skip C09 if already there).

### Block selection

- Prefer hotbar slots with placeable blocks (exclude non-full / bad blocks per existing Mc helpers or a small allowlist consistent with AutoPlace).
- Choose the stack with the **largest count**; ties → lowest slot index.
- Server place slot = that slot. Visual slot = spoof slot from enable (or last non-block slot policy: keep the slot the player had when Scaffold enabled).

### Item spoof

- Gameplay: set `currentItem` to place slot when placing (OpenMyau-style), so C08/C09 match reality.
- Render: temporarily show visual slot (hotbar + held item) via restore of `ScaffoldItemSpoofHook` + `MixinGuiIngame` (and any hand render hooks needed for parity).
- If place slot == visual slot → no C09 on “switch” or “restore”.

### Target finding

- **Bridge (KeepY Off):** when block under / next foothold is replaceable air, find a neighbor support face to place against (standard under-player / edge place).
- **Tower:** while jump is held and space under feet is replaceable, prefer **UP** face of block below (place under feet).
- **Telly rising:** do not require place; look forward (movement yaw, pitch suitable for walking).
- **Telly falling:** same target find as Off, then place so the player does not fall through.

### Aim modes

All modes produce a place hit on the chosen face when placing; GCD-quantize after step.

| Mode | Target look |
|------|-------------|
| **Backwards** | Yaw ≈ movement yaw + 180°; pitch toward place hit on support face |
| **GodBridge** | Diagonal yaw (≈ movement ±45° class) so A+S / D+S godbridge works; pitch toward place hit |
| **Nearest** | Rotation to the nearest point on the placeable face from eye |
| **Sideways** | Among yaw offsets that still ray-hit the face for place, pick the one with largest \|angle from movement forward\| (most sideways) |

When Telly is **looking forward** (not placing): ignore aim mode for yaw (use movement forward); pitch near neutral / walk pitch. When Telly **places** (falling), use the selected aim mode.

### Rotation step

1. Compute raw target yaw/pitch from aim mode (or Telly forward).
2. Each tick: `speed = randomUniform(min, max)` (integers OK); step current silent yaw/pitch toward target with that speed on both axes (same formula family as KA silent step).
3. Apply mouse GCD quantization (same helper family KA / old Scaffold used).
4. `PlayerUpdateHook.requestRotation(sentYaw, sentPitch)`.
5. `RotationState.applyState(true, sentYaw, sentPitch, pervYaw=sentYaw, priority=3)` so MoveFix and F5 see the sent look.

Place only when the silent raycast hits the intended interact face (or equivalent safe gate). If not yet on target, keep stepping without placing.

### MoveFix

- Always on while Scaffold is enabled and has an armed silent look.
- Remap movement input via `MoveFixUtil.fixStrafe(cameraYaw, RotationState.getSmoothedYaw(), sneak)` from `MovementInputHook` / Scaffold patch (same pattern as KA).

### KeepY

| Mode | Behavior |
|------|----------|
| **Off** | Normal bridge: place when needed under/at feet; no auto-jump |
| **Telly** | When bridging needs extension: set jump input; while `onGround` or airborne with `motionY >= 0`, look **forward** (no place required); when airborne and `motionY < 0`, aim with selected mode and place |

Tower (jump held + under-feet place) works in both modes without extra motion hacks.

### Sprint

- v1: **do not sprint while Scaffold is enabled** (walk bridge). Suppress sprint key + `setSprinting(false)`. Sprint module yields to Scaffold.

## Files (expected)

| File | Role |
|------|------|
| `module/modules/player/scaffold/ScaffoldModule.java` | Module, settings, tick/place orchestration |
| `.../ScaffoldAim.java` (or similar) | Aim mode math |
| `.../ScaffoldBlocks.java` | Stack pick + placeable check |
| `runtime/ScaffoldItemSpoofHook.java` | Render spoof (restore) |
| `mixin/.../MixinGuiIngame.java` | Hotbar spoof redirect (restore) |
| `GnuClientMod.java` | Register module |
| `PlayerUpdateHook.java` / `MovementInputHook.java` | Re-wire Scaffold static hooks |
| `KillAuraAutoBlock.isPlacing()` | Remains name-based; works once module registered |

Keep the package small vs the deleted tree. Prefer pure helpers + one module over technique/tower/feature graphs.

## Verification

- `./gradlew build`
- ClickGUI: Aim, KeepY, Rotation min/max only (defaults 60/80)
- F5 shows silent head look; movement follows MoveFix while scaffolding
- Biggest stack used for place; hotbar visually unchanged; no C09 when already on that slot
- KeepY Off bridges; Telly auto-jumps, looks forward on rise, places on `motionY < 0`
- Hold jump towers in Off and Telly without motion cheats
- Disable restores visual slot without redundant C09 when unchanged
