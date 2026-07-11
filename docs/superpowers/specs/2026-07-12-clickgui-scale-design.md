# ClickGUI Scale — design

**Date:** 2026-07-12  
**Status:** ready for user review  
**Ship path:** `gnuclient recode/`  
**Approved:** Uniform scale via config (**A**), approach **1** (GL/matrix scale + inverse mouse)

## Problem

ClickGUI size is fixed aside from Minecraft’s own GUI scale. Users want bigger/smaller panels through client config.

## Goals

- Slider on ClickGUI settings: whole menu scales up/down.
- Persist with existing ClickGUI module config.
- Clicks, drag, scroll stay aligned with drawn UI.

## Non-goals

- Separate panel-width control.
- Changing Minecraft video “GUI Scale”.
- Per-column scale.
- Vault architecture note (config-only).

## Settings

| Setting | Type | Default | Range | Step |
|---------|------|---------|-------|------|
| Scale | slider | 1.0 | 0.75–1.50 | 0.05 |

Lives on `ClickGuiModule` next to Font / Blur / Animation speed / Panel opacity.

## Architecture

1. **`ClickGuiModule`** — add `Scale` slider + `getScale()` / `getScaleSetting()`.
2. **`ClickGuiScreen`** — when rendering/handling input:
   - `userScale = ClickGuiModule.instance().getScale()` (clamp if null → 1.0)
   - Combined factor for pixel-align/scissor: `sr.getScaleFactor() * userScale` (or apply userScale via GL after SR and pass `userScale` into column render consistently — pick one scheme and use it for both draw and hit-test).
   - Preferred scheme: translate to a stable origin (e.g. screen center or top-left), `GlStateManager.scale(userScale, userScale, 1)`, draw existing layout in unscaled logical coords; transform mouse: `logicalX = mouseX / userScale` (and same for Y) relative to that origin.
3. **Hit-testing** — all mouse paths (`mouseClicked`, `mouseReleased`, `handleMouseInput`, drag) use the same logical coords as render.
4. **Layout persistence** — column x/y stay in logical (unscaled) space so changing Scale does not corrupt saved positions.

## Testing

- Unit: Scale default/min/max/step on `ClickGuiModule` (mirror other ClickGUI setting tests if present).
- Manual: Scale 0.75 / 1.0 / 1.50 — panels resize; click modules/settings/drag still work; reopen after config save keeps Scale.

## Risks

| Risk | Mitigation |
|------|------------|
| Scissor/blur mismatch | One combined scale factor for `PixelAlign` / scissor |
| Mouse desync | Inverse of same transform as GL scale |
| Null module instance | Default scale 1.0 |
