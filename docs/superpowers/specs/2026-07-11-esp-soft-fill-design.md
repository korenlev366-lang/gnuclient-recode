# ESP soft fill-only overlays

**Date:** 2026-07-11  
**Status:** ready for user review  
**Ship path:** `gnuclient recode/`

## Problem

World ESP overlays (player ESP, BedESP, ItemESP, Blink/Lagrange Server ESP, Backtrack server boxes) use harsh wireframe outlines via `RenderHelper.drawBoundingBox`. That look does not match the softer ClickGUI / arraylist / notification surfaces. User preference: **soft fill only — no outlines at all** for these boxes.

## Goals

- One shared fill-only draw path for all listed ESP boxes.
- Soft default fill alpha (~0.16), tunable in one place.
- Remove outline-related settings and draw calls from those modules.
- Keep existing per-module RGB / role colors (no palette redesign in this change).

## Non-goals

- Retheming ClickGUI, arraylist, or notifications.
- Changing nametag fonts/labels.
- Removing Backtrack trails, pulse, glow, hurt, or other non-box effects.
- Deleting `RenderHelper.drawBoundingBox` / `drawLine3D` (still used by non-ESP overlays).

## Decision

**Approach:** shared `EspDraw` helper (recommended and approved).

## Architecture

### `gnu.client.util.EspDraw`

New small helper next to `RenderHelper`:

- `DEFAULT_FILL_ALPHA` ≈ `0.16f`
- `box(minX, minY, minZ, maxX, maxY, maxZ, r, g, b)` — fills with default alpha
- `box(..., alpha)` — optional override
- Uses `RenderHelper.drawFilledBox` under the hood
- Callers that already wrap `RenderHelper.begin()`/`end()` may use a `boxInBatch(...)` variant that does not push/pop GL state, **or** modules keep their existing begin/end and only swap the draw call to filled-only via `EspDraw` that documents batching rules clearly

**Batching rule (explicit):** Prefer one `RenderHelper.begin()` / many fills / one `end()` per module frame. `EspDraw` must not nest begin/end if the caller already began a batch — expose:

- `fill(...)` — draw only (caller owns begin/end)
- optional convenience `draw(...)` that begin+fill+end for single-box cases

### Modules in scope

| Module | Change |
|--------|--------|
| `EspModule` | Fill via `EspDraw`; remove `Filled`, `Line Width` |
| `BedEspModule` | Fill via `EspDraw`; remove `Filled` |
| `ItemEspModule` | Fill via `EspDraw`; drop outline pass (keep item color map) |
| `BlinkModule` Server ESP | Fill via `EspDraw`; remove `Filled`, `Line Width` |
| `LagrangeModule` Server ESP | Same as Blink |
| `BacktrackModule` server/ghost box | Fill via `EspDraw` when box enabled; remove outline/fill/line-width settings listed below |

### Backtrack settings cleanup

**Remove:** `DrawOutline`, `DrawFill`, `OutlineColorR/G/B`, `Line width`, `Outline width` (and their `visibleWhen` wiring).

**Keep:** `Enable visuals`, `RenderServerRecord`, `Render mode`, `DrawBox` (master toggle for soft fill), `BoxColorR/G/B`, trail/pulse/glow/hurt settings.

`drawGhostBox` becomes fill-only through `EspDraw` when `DrawBox` is on; no bounding-box branch.

### Config compatibility

Unknown/removed setting keys in saved configs are ignored on load (existing behavior). No migration required.

## Visual mockup

Reference: `docs/mockups/esp-vs-hud-style.html` (update “proposed” side to fill-only / no outline when implementing).

## Verification

1. `./gradlew compileJava` (or `build`) succeeds.
2. ClickGUI: no Filled / Line Width / outline settings on scoped modules; Backtrack outline settings gone.
3. In-game: ESP, BedESP, ItemESP, Blink/Lagrange Server ESP, Backtrack box — soft translucent fills only, no wireframes.
4. Backtrack trails/lines still render when enabled.

## Risks

- Soft fill alone can be harder to see on bright blocks — mitigated by keeping RGB sliders and a single alpha constant to tune.
- Backtrack historically had separate outline colors; after removal, box uses `BoxColor*` only.
