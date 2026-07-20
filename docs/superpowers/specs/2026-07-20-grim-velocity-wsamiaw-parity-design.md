# Grim Velocity — wsamiaw parity design

**Date:** 2026-07-20  
**Status:** ready for user review  
**Ship path:** `gnuclient recode/`  
**Reference:** `wsamiaw/.../Velocity.java` mode `GRIM` (index 5)

## Problem

GNUClient `Grim` cancels self `S12` after hurt and sends `C07` dig. wsamiaw `GRIM` does the opposite family of bypass: **let S12 through**, **adaptively scale** knockback, detect **setbacks**, optionally **delay C0F** confirmations. User chose to replace dig-cancel `Grim` with wsamiaw behavior (option A — dig gone; `GrimReduce` unchanged).

## Goals

- Rewrite `GrimVelocity` to match wsamiaw mode-5 behavior.
- Add Grim-gated settings matching wsamiaw defaults/ranges.
- Apply scale via existing `IAccessorS12PacketEntityVelocity` (no new KnockbackEvent).
- Keep `GrimReduce` and all other modes untouched.

## Non-goals

- Porting `GRIMV2`, `ATTACK_REDUCE`, `AGC`, or other wsamiaw modes.
- Preserving dig-cancel as a separate mode.
- Changing KnockbackDelay ownership rules (still skip when KD owns inbound).
- Explosion scaling special-cased for Grim (wsamiaw GRIM uses shared explosion path only outside mode-5 early return; mode 5 clears `pendingExplosion` and returns — no explosion mutate in GRIM path). Match that: Grim path does not specially handle `S27`.

## Approved decisions

1. Drop dig-cancel from `Grim` (user **A**).
2. Full parity approach (user **1**): adaptive scale + setback + optional C0F delay + settings.

## Behavior (wsamiaw → gnuclient)

### State (owned by `GrimVelocity`)

| Field | Default / reset on enable |
|-------|---------------------------|
| `grimCurrentH` | `grimStartH` (0.3) |
| `grimCurrentV` | `grimStartV` (0.0) |
| `grimAdaptTimer` | 0 |
| `grimSetback` | false |
| `grimTxTimer` | 0 |
| `pendingSetbackCheck` | false |
| `delayedTransactions` | empty `Deque<C0F>` |

On disable: flush any held C0Fs, clear deque/timers.

### Settings (`VelocityModule`, `visibleWhen` mode `Grim`)

| Setting | Default | Range | Maps from |
|---------|---------|-------|-----------|
| Start Horizontal | 0.3 | 0–1 step 0.01 | `grim-start-horizontal` |
| Start Vertical | 0.0 | 0–1 | `grim-start-vertical` |
| Adapt Step | 0.05 | 0.01–0.2 | `grim-adapt-step` |
| Min Horizontal | 0.0 | 0–1 | `grim-min-horizontal` |
| Max Horizontal | 1.0 | 0–1 | `grim-max-horizontal` |
| Adapt Ticks | 20 | 5–100 int | `grim-adapt-ticks` |
| Transactions | true | bool | `grim-transactions` |
| Tx Delay | 2 | 1–10 int | `grim-tx-delay` |

Also: show existing **Chance** when mode is `Grim` (wsamiaw rolls chance on every GRIM knockback). Keep using shared `chanceCounter` on `VelocityModule`. Honor existing **Fake Check** / `allowNext` like wsamiaw: only scale when `!allowNext || !fakeCheck`; set `allowNext = false` on self `S19` opcode 2, reset `allowNext = true` after handling the scale path.

### Receive — self `S12`

Do **not** cancel the packet.

1. If `pendingSetbackCheck`: clear flag; set `grimSetback`; `grimTxTimer = 0`; flush delayed C0Fs; snap `grimCurrentH = maxH`, `grimCurrentV = 1.0`. Still continue to scale this packet with (now max) currents after chance roll — matching “take full” snap then apply currents (currents are max so scale ≈ full).
2. Else: `grimSetback = false`; `grimAdaptTimer = 0`; if transactions enabled, `grimTxTimer = txDelay`.
3. Fake-check / chance gate as above.
4. If chance fires: mutate packet motion:
   - H: `motionX/Z *= currentH` (if `currentH == 0` → set 0)
   - V: `motionY *= currentV` (if `currentV == 0` → set 0)
5. Return `false` (never cancel).

### Receive — `S08` player pos look

If `dx²+dy²+dz² > 4.0`: `grimSetback = true`, `pendingSetbackCheck = true`.

### Receive — self `S19` opcode 2

`allowNext = false` (for fake-check gate). Do not cancel.

### Send — `C0F`

If transactions on and `grimTxTimer > 0`: offer packet to deque, return `true` (cancel send).

### Tick PRE

1. `grimAdaptTimer++`; when `>= adaptTicks`: reset timer; if `grimSetback` then raise H/V by step (cap maxH / 1.0) and clear setback flag; else lower H/V by step (floor minH / 0.0).
2. If `grimTxTimer > 0`: decrement; on 0 flush deque via normal send queue (`Mc.addToSendQueue` / net handler — **not** `sendPacketNoEvent`, so listeners see flush; match wsamiaw `addToSendQueue`).

### Flush helper

Drain deque oldest-first; skip if net handler null.

## Files

| File | Change |
|------|--------|
| `velocity/GrimVelocity.java` | Full rewrite |
| `VelocityModule.java` | New Grim settings + visibility; Chance includes `Grim`; no dig leftovers |

No mixin changes expected.

## Verification

1. `./gradlew build` in `gnuclient recode/`.
2. Mode `Grim`: settings appear; other modes hide them.
3. In-game (manual): take hits — motion reduced vs dig-cancel; after large rubberband, reduction backs off; with Transactions on, brief C0F hold after S12 then flush.
4. `GrimReduce` still dig/attack path unchanged.

## Out of scope follow-ups

- Vault decision note (optional if treated as module behavior port, not architecture change).
- Porting GRIMV2 later.
