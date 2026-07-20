# Lux Watermark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Top-left Lux accent-rim watermark (`Lux | version | FPS`) toggled via HUD → Watermark.

**Architecture:** Add `Watermark` bool on `HudModule`; draw pill in `HudRenderer` with UiKit tokens; fix early-return so watermark-only still renders; version from `GnuClientMod.VERSION`, FPS from `Minecraft.getDebugFPS()`.

**Tech Stack:** Forge 1.8.9, existing `UiKit` / `UiFont` / `HudRenderer`, JUnit.

**Spec:** `docs/superpowers/specs/2026-07-20-lux-watermark-design.md`

---

## File map

| File | Responsibility |
|------|----------------|
| `module/modules/visual/HudModule.java` | `Watermark` setting, `wantsWatermark()`, `shouldDrawOverlay()` |
| `ui/hud/HudRenderer.java` | `drawWatermark`, early-return fix |
| `src/test/.../HudModuleWatermarkTest.java` | Gate logic unit tests |

---

### Task 1: HudModule Watermark setting + overlay gate (TDD)

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/visual/HudModule.java`
- Create: `src/test/java/gnu/client/module/modules/visual/HudModuleWatermarkTest.java`

- [ ] **Step 1: Write failing tests**

```java
package gnu.client.module.modules.visual;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import org.junit.Test;

import static org.junit.Assert.*;

public class HudModuleWatermarkTest {

    @Test
    public void watermarkSettingDefaultsOn() {
        HudModule hud = new HudModule();
        BoolSetting wm = null;
        for (Setting<?> s : hud.getSettings()) {
            if ("Watermark".equals(s.getName()))
                wm = (BoolSetting) s;
        }
        assertNotNull(wm);
        assertTrue(wm.isToggled());
        assertTrue(hud.wantsWatermark());
    }

    @Test
    public void wantsWatermarkFollowsToggle() {
        HudModule hud = new HudModule();
        BoolSetting wm = find(hud, "Watermark");
        wm.setValue(false);
        assertFalse(hud.wantsWatermark());
        wm.setValue(true);
        assertTrue(hud.wantsWatermark());
    }

    @Test
    public void shouldDrawOverlayWhenOnlyWatermark() {
        HudModule hud = new HudModule();
        hud.setEnabled(true);
        find(hud, "Array").setValue(false);
        find(hud, "Notifications").setValue(false);
        find(hud, "Watermark").setValue(true);
        assertTrue(HudModule.shouldDrawOverlay());
    }

    @Test
    public void shouldNotDrawOverlayWhenHudDisabled() {
        HudModule hud = new HudModule();
        hud.setEnabled(false);
        find(hud, "Watermark").setValue(true);
        assertFalse(HudModule.shouldDrawOverlay());
    }

    private static BoolSetting find(HudModule hud, String name) {
        for (Setting<?> s : hud.getSettings()) {
            if (name.equals(s.getName()))
                return (BoolSetting) s;
        }
        fail("missing " + name);
        return null;
    }
}
```

Note: `shouldDrawOverlay()` uses `instance()` — constructing `new HudModule()` sets `instance` in constructor (same as existing ClickGuiModuleTest pattern). Disable may trigger HUD side effects — acceptable in unit test.

- [ ] **Step 2: Run — expect FAIL**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.module.modules.visual.HudModuleWatermarkTest --no-daemon
```

Expected: FAIL (missing Watermark / wantsWatermark).

- [ ] **Step 3: Implement HudModule changes**

```java
private final BoolSetting showWatermark = addSetting(new BoolSetting("Watermark", true));

public boolean wantsWatermark() {
    return showWatermark.getValue();
}

public static boolean shouldDrawOverlay() {
    HudModule hud = instance();
    if (hud == null || !hud.isEnabled()) {
        return false;
    }
    if (hud.wantsArray()) {
        return true;
    }
    if (hud.wantsWatermark()) {
        return true;
    }
    return hud.wantsNotifications() && hasActiveNotifications();
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.module.modules.visual.HudModuleWatermarkTest --no-daemon
```

- [ ] **Step 5: Commit** (only if user asked).

---

### Task 2: HudRenderer drawWatermark + early-return fix

**Files:**
- Modify: `src/main/java/gnu/client/ui/hud/HudRenderer.java`

- [ ] **Step 1: Fix early return and call drawWatermark**

Current code returns before GL if `!drawArray && !drawToasts`. Change to:

```java
final boolean drawArray = hud.wantsArray() && !rows.isEmpty();
final boolean drawToasts = hud.wantsNotifications() && notifications.hasActive();
final boolean drawWatermark = hud.wantsWatermark();
if (!drawArray && !drawToasts && !drawWatermark) {
    return;
}

// inside GlGuard.run:
if (drawWatermark) {
    drawWatermark(scale);
}
if (drawArray) { ... }
if (drawToasts) { ... }
```

- [ ] **Step 2: Implement drawWatermark**

```java
private static final float WM_MARGIN = ARRAY_MARGIN; // 12f
private static final float WM_PAD_X = 10f;
private static final float WM_PAD_Y = 7f;
private static final float WM_GAP = 6f;
private static final float WM_RADIUS = 12f;
private static final float WM_RIM = 1f;

private void drawWatermark(float scale) {
    String brand = "Lux";
    String ver = gnu.client.GnuClientMod.VERSION;
    int fps = net.minecraft.client.Minecraft.getDebugFPS();
    String fpsText = fps + " FPS";
    String sep = " | ";

    float brandW = UiFont.width(brand);
    float sepW = UiFont.width(sep);
    float verW = UiFont.width(ver);
    float fpsW = UiFont.width(fpsText);
    float textH = UiFont.height();
    float innerW = brandW + sepW + verW + sepW + fpsW;
    float w = innerW + WM_PAD_X * 2f;
    float h = textH + WM_PAD_Y * 2f;

    float x = UiKit.PixelAlign.snap(WM_MARGIN, scale);
    float y = UiKit.PixelAlign.snap(WM_MARGIN, scale);
    w = UiKit.PixelAlign.snap(w, scale);
    h = UiKit.PixelAlign.snap(h, scale);

    // Accent rim: outer ACCENT, then inset SURFACE_STRONG
    UiKit.drawRoundedPanel(x - WM_RIM, y - WM_RIM, w + WM_RIM * 2f, h + WM_RIM * 2f,
            WM_RADIUS + WM_RIM, UiKit.ACCENT);
    UiKit.drawRoundedPanel(x, y, w, h, WM_RADIUS, UiKit.SURFACE_STRONG);

    float tx = x + WM_PAD_X;
    float ty = UiKit.PixelAlign.snap(y + (h - textH) * 0.5f, scale);

    UiFont.draw(brand, tx, ty, UiKit.ACCENT);
    tx += brandW;
    UiFont.draw(sep, tx, ty, UiKit.MUTED_DIM);
    tx += sepW;
    UiFont.draw(ver, tx, ty, UiKit.MUTED);
    tx += verW;
    UiFont.draw(sep, tx, ty, UiKit.MUTED_DIM);
    tx += sepW;
    UiFont.draw(fpsText, tx, ty, UiKit.MUTED);
}
```

If `UiFont.width(String)` / `height()` signatures differ, match existing HudRenderer array drawing (see `drawArrayList`). Prefer same font size as array rows unless watermark needs explicit size overload.

Import `GnuClientMod` and `Minecraft` at top of file (or FQCN as above).

- [ ] **Step 3: Compile + remapJar**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.module.modules.visual.HudModuleWatermarkTest compileJava remapJar --no-daemon
```

Expected: SUCCESS.

- [ ] **Step 4: Commit** (only if user asked).

---

### Task 3: Manual verification

- [ ] HUD on, Watermark on → top-left Lux pill with live FPS  
- [ ] Watermark off → gone  
- [ ] Array off + Notifications off + Watermark on → only watermark  
- [ ] HUD off → nothing  
- [ ] Rim/colors match Lux ClickGUI accent  

---

## Spec coverage

| Spec | Task |
|------|------|
| Watermark bool default on | Task 1 |
| shouldDrawOverlay watermark-only | Task 1 |
| Accent-rim Lux pill | Task 2 |
| VERSION + debug FPS | Task 2 |
| Top-left 12px margin | Task 2 |

## Out of scope

Dragging, blur, extra stats, separate module.
