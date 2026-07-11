# KillAura autoblock + NoSlow — OpenMyau parity (GNUClient Forge)

**Status:** approved design (approach B + NoSlow + wired FloatManager)  
**Date:** 2026-07-11  
**Ship path:** `gnuclient recode/`  
**References:**
- `OpenMyau-Plus` — `KillAura.java` (autoBlock switch / startBlock / stopBlock / interactAttack)
- `OpenMyau-Plus` — `NoSlow.java`, `MixinEntityPlayerSP` (`isUsingItem` redirect)
- `OpenMyau-Plus` — `FloatManager`, `FloatModules`, `BlinkManager`, `BlinkModules`

---

## Problem

Standalone AutoBlock was removed from GNUClient. KillAura still needs OpenMyau-style autoblock modes (including Hypixel coupling to NoSlow). GNUClient has no NoSlow module, no FloatManager, and no shared BlinkModules ownership for AUTO_BLOCK / NO_SLOW.

## Goals

- Full OpenMyau KillAura autoblock mode set on KillAura (default **NONE**)
- Approach **B**: settings on KillAura; logic in `KillAuraAutoBlock` helper
- Port OpenMyau **NoSlow** (default off) so Hypixel autoblock can gate on `NoSlow.isEnabled()`
- Port **FloatManager** + **FloatModules.NO_SLOW**, **wired** from NoSlow when `isAnyActive()`
- Port shared **BlinkManager** + **BlinkModules** (incl. `AUTO_BLOCK`, `NO_SLOW`); KA blink modes own via `AUTO_BLOCK`
- Keep existing Grim guards: skip attack after RELEASE same tick; cancel sword world-C08 while KA on + sword + Scaffold off

## Non-goals

- Reviving standalone `AutoBlockModule`
- Implementing AntiVoid / NoFall / Displace consumers (enum stubs only if needed for parity)
- Activating `BlinkModules.NO_SLOW` packet hold unless a GRIM NoSlow path needs it in this pass (slot reserved)
- Changing user-facing `BlinkModule` (Raven hold) behavior beyond ownership coexistence

## Design

### 1. KillAura autoblock settings

Owned by `KillAuraModule` (not a separate module).

| Setting | Type | Values / range | Default |
|---------|------|----------------|---------|
| Auto-block | mode | NONE, VANILLA, SPOOF, HYPIXEL, BLINK, INTERACT, SWAP, LEGIT, FAKE | **NONE** |
| AutoBlockCPS | number | OpenMyau-equivalent range | match OpenMyau |
| AutoBlockRange | number | OpenMyau-equivalent range | match OpenMyau |
| AutoBlockRequirePress | bool | — | match OpenMyau |

### 2. `KillAuraAutoBlock` helper

| Path | Role |
|------|------|
| `gnu.client.module.modules.combat.killaura.KillAuraAutoBlock` (or adjacent package matching existing KA helpers) | Mode switch, startBlock / stopBlock / interactAttack, CPS/range/press gates |

- Called from KillAura preUpdate / combat tick path (same timing family as OpenMyau).
- **Hypixel:** before stopBlock, if `NoSlowModule` enabled → random hotbar C09 flick then restore (OpenMyau case 3).
- **Blink / Interact / Hypixel blinkReset:** use shared `BlinkManager.setBlinkState(..., AUTO_BLOCK)` — not a private KA-only queue.
- **FAKE:** animation / client block state only; no real block packets if that matches OpenMyau.
- On KA disable / world change: force `setBlinkState(false, AUTO_BLOCK)` and stopBlock cleanup.

### 3. NoSlow module

| Field | Value |
|-------|--------|
| Class | `gnu.client.module.modules.movement.NoSlowModule` (or Player if category convention prefers) |
| Name | `NoSlow` |
| Default | **disabled** |

**Settings (OpenMyau):**

| Setting | Default / notes |
|---------|-----------------|
| sword-mode | NONE / **VANILLA** / GRIM (default VANILLA index like OpenMyau) |
| sword-motion % | 100 (visible when VANILLA) |
| sword-sprint | true |
| killaura-only | false |
| food-mode / food-motion / food-sprint | NONE / … |
| bow-mode / bow-motion / bow-sprint | NONE / … |

**API for KA / mixins:**

- `isEnabled()`, `isSwordActive()`, `isFoodActive()`, `isBowActive()`, `isAnyActive()`, `canSprint()`, `getMotionMultiplier()`

**Core motion effect:** mixin redirect on `EntityPlayerSP.onLivingUpdate` so `isUsingItem()` returns **false** while module enabled and `isAnyActive()` — skips vanilla 0.2× slow (OpenMyau `MixinEntityPlayerSP`).

**Float wiring (approved A):** while enabled and `isAnyActive()`, `FloatManager.setFloatState(true, NO_SLOW)`; otherwise `false`. Clear on disable.

### 4. FloatManager

| Path | Role |
|------|------|
| `gnu.client.runtime.FloatManager` (or `management` package if one exists) | LinkedHashMap of `FloatModules` → bool; `floating` predicted flag |
| `gnu.client.runtime.FloatModules` enum | At least `NO_SLOW` |

**Behavior (OpenMyau):** on player update, if `hasActiveModule() || isPredicted()` and `isFalling()` (onGround + posY delta &lt; 0 + motionY &lt; 0) → `setPosition(x, y + 0.001, z)` and set `floating = true`; else `floating = false`.

Register with existing event bus / client listener lifecycle (same pattern as other runtime managers).

### 5. BlinkManager + BlinkModules

| Path | Role |
|------|------|
| `gnu.client.runtime.BlinkManager` (shared) | Own outbound queue; `offerPacket`; `setBlinkState(state, module)`; flush on release |
| `gnu.client.runtime.BlinkModules` enum | `NONE`, `ANTI_VOID`, `AUTO_BLOCK`, `BLINK`, `DISPLACE`, `NO_FALL`, `NO_SLOW` |

**Ownership rules:**

- Only the owning module may release its blink (`setBlinkState(false, owner)` no-ops if owner mismatch — OpenMyau).
- KA autoblock blink paths use **`AUTO_BLOCK`**.
- **`NO_SLOW`:** reserved; do not activate in this pass unless implementation discovers a required GRIM packet-hold path (document if activated).
- Coexist with `BlinkModule` and Scaffold blink: do not steal another owner’s queue; document interaction (pause Lagrange when holding, reuse `PacketHelper` blink exempts).

Wire `offerPacket` into the existing outbound packet intercept path (PacketEvents / PacketHelper) so AUTO_BLOCK holds work without requiring the user Blink module on.

### 6. Grim / packet guards (KEEP)

- `AuraCombatPacketGuard`: after RELEASE_USE_ITEM, KillAura skips attack until next tick (PacketOrderI).
- Cancel non–use-item C08 (dir ≠ 255) while KA on + sword + Scaffold off; keep use-item 255 (RotationPlace).
- Scaffold placement remains exempt.

### 7. Registration

- `GnuClientMod`: register `NoSlowModule`; construct/register `FloatManager` + `BlinkManager` if not already present.
- Mixins JSON: EntityPlayerSP NoSlow redirect (dedicated mixin preferred).
- Vault: decision notes for KA autoblock-on-KA, NoSlow, FloatManager wiring, shared BlinkManager.

## Verification

| Case | Expected |
|------|----------|
| Auto-block NONE | No block packets from KA helper |
| VANILLA / SPOOF / LEGIT / FAKE / SWAP | Match OpenMyau packet / animation behavior for that mode |
| HYPIXEL + NoSlow off | stopBlock without C09 flick |
| HYPIXEL + NoSlow on | C09 flick then stopBlock |
| BLINK / INTERACT | Packets held under `AUTO_BLOCK`; flush on release; no fight with user Blink when off |
| NoSlow on + blocking sword | No vanilla use-item slow; FloatManager float state true while using |
| NoSlow off | Vanilla slow; float state false |
| KA off | AUTO_BLOCK blink released; block stopped |
| Build | `./gradlew build` succeeds; jar stages |

## Files (planned)

| Path | Role |
|------|------|
| `.../combat/KillAuraModule.java` | Settings + hook into helper |
| `.../combat/.../KillAuraAutoBlock.java` | Mode logic |
| `.../movement/NoSlowModule.java` | Module + float wiring |
| `.../mixin/.../MixinEntityPlayerSPNoSlow.java` (or extend existing) | `isUsingItem` redirect |
| `.../runtime/FloatManager.java` + `FloatModules.java` | Float nudge |
| `.../runtime/BlinkManager.java` + `BlinkModules.java` | Shared blink ownership |
| `GnuClientMod.java` | Register module + managers |
| `mixins.gnuclient.json` | Mixin entry |
| `gnu client/` vault Decision notes | Architecture log |

## Open questions

None — defaults, approach B, NoSlow coupling, Float wiring (A), and BlinkModules scope locked.
