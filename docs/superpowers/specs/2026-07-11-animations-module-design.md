# Animations module — OpenMyau parity (GNUClient Forge)

**Status:** approved design (approach B)  
**Date:** 2026-07-11  
**Ship path:** `gnuclient recode/`  
**Reference:** `OpenMyau-Plus` — `Animations.java`, `MixinItemRendererAnimations.java`, `MixinEntityLivingBaseAnimations.java`, `AnimationMode` / `AnimationConfig` (syuto/animations-1.6)

---

## Problem

GNUClient has no first-person sword block / swing animation customization. OpenMyau exposes 16 styles plus item scale and swing speed via ItemRenderer + EntityLivingBase mixins. Users want that behavior on the Forge recode path.

## Goals

- Full OpenMyau mode/transform/scale/swing-speed parity
- Module **default enabled** (OpenMyau default)
- GNUClient-native wiring (no `AnimationConfig` sync layer)
- Coexist with existing `MixinEntityLivingBase` (no conflicting `@Overwrite` of swing)

## Non-goals

- Third-person-only cosmetics
- Changing AutoBlock / KillAura block logic
- Porting OpenMyau UI animation utilities (`util/animations/*`)

## Design

### Module

| Field | Value |
|-------|--------|
| Class | `gnu.client.module.modules.visual.AnimationsModule` |
| Name | `Animations` |
| Category | `VISUALS` |
| Default | **enabled** |
| HUD suffix | current mode display name (e.g. `Exhibition`) |

**Settings**

| Setting | Type | Range / values | Default |
|---------|------|----------------|---------|
| Mode | mode | VANILLA, EXHIBITION, ETB, SIGMA, DORTWARE, PLAIN, SPIN, AVATAR, SWONG, SWANG, SWANK, STYLES, NUDGE, PUNCH, JIGSAW, SLIDE | VANILLA |
| Scale | int/slider | 50–150 | 100 |
| SwingSpeed | int/slider | 0–100 | 0 |

Mixins read `AnimationsModule.instance()` (or `ModuleManager.getModule("Animations")`) and apply only when `isEnabled()`.

### ItemRenderer hook

New mixin: `gnu.client.mixin.impl.render.MixinItemRendererAnimations` (priority high enough to win the block-path transform, e.g. 999 like OpenMyau).

1. **`@Redirect`** the `transformFirstPersonItem(FF)` invoke used on the **block** path (`ordinal = 2` in OpenMyau) — no-op when module enabled so custom transforms replace it; when disabled, call original.
2. **`@Inject`** before `doBlockTransformations()` — apply OpenMyau GL math for the selected mode (if-else, not switch, to avoid synthetic `$SwitchMap` mixin class issues).
3. **`@Inject`** before `renderItem(...)` — `glScaled(scale/100)`.
4. **Accessor** `IAccessorItemRenderer` for `equippedProgress` / `prevEquippedProgress` (add under `mixin.impl.accessors` if missing).
5. Shadow/`@Invoker` or protected call to `transformFirstPersonItem` as OpenMyau does.

Spin mode keeps a mixin-local `spin` float updated from `System.currentTimeMillis()`.

### Swing speed hook

Inject into `EntityLivingBase.getArmSwingAnimationEnd` (SRG + MCP names) with **cancellable `@Inject` HEAD**, not `@Overwrite`:

- Module disabled → leave vanilla (return 6 via not cancelling, or cancel with 6 only if we must).
- Module enabled → `cir.setReturnValue((int)(6.0 + pct / 100.0 * 14.0))` where `pct = clamp(SwingSpeed, 0, 100)`.

Prefer a small dedicated mixin class or an inject in existing `MixinEntityLivingBase` — dedicated `MixinEntityLivingBaseAnimations` is clearer and matches OpenMyau separation.

### Registration

- `GnuClientMod.registerModules()` → `safeRegister(new AnimationsModule())`
- `mixins.gnuclient.json` → register ItemRenderer animations mixin, ItemRenderer accessor, living-base swing inject mixin

### Config / persistence

Standard module settings via existing config save — no separate AnimationConfig file.

## Parity notes / intentional KEEP

- Transforms copied from OpenMyau `MixinItemRendererAnimations` (same constants).
- Block-path only (same as OpenMyau); non-block first-person uses vanilla unless scale inject still applies — **match OpenMyau**: scale inject runs before every `renderItem` in `renderItemInFirstPerson`; mode transforms only on block path.
- AutoBlock `forceBlockAnimation` remains independent; Animations only changes how blocking looks when the block transform path runs.

## Verification

| Case | Expected |
|------|----------|
| Module on, Mode Exhibition, holding sword + blocking | Exhibition GL path; not vanilla block bob |
| Cycle all 16 modes | Distinct poses; no crash / mixin switchmap error |
| Scale 50 / 150 | Item visibly smaller / larger in FP |
| SwingSpeed 0 vs 100 | Faster vs slower arm swing duration |
| Module off | Vanilla ItemRenderer + swing length 6 |
| Build | `./gradlew build` succeeds; jar stages |

## Files (planned)

| Path | Role |
|------|------|
| `.../visual/AnimationsModule.java` | Module + settings + instance |
| `.../mixin/impl/render/MixinItemRendererAnimations.java` | Block transforms + scale |
| `.../mixin/impl/accessors/IAccessorItemRenderer.java` | equipped progress fields |
| `.../mixin/impl/entity/MixinEntityLivingBaseAnimations.java` | Swing speed inject |
| `GnuClientMod.java` | Register module |
| `mixins.gnuclient.json` | Register mixins |

## Open questions

None — scope and defaults locked (full parity, default on, approach B).
