# ClickGUI Scale Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a ClickGUI **Scale** slider (0.75–1.50) that uniformly scales the menu with correct mouse hit-testing.

**Architecture:** Persist scale on `ClickGuiModule`. In `ClickGuiScreen`, apply `GlStateManager.scale(userScale)` from top-left (0,0) in scaled GUI space, pass Minecraft `sr.getScaleFactor()` unchanged into PixelAlign/scissor, and convert all mouse coords with `logical = raw / userScale`. Column layout positions stay logical.

**Tech Stack:** Java 8, Forge 1.8.9, existing `ClickGuiModule` / `ClickGuiScreen` / JUnit.

**Spec:** `docs/superpowers/specs/2026-07-12-clickgui-scale-design.md`

**Commits:** Skip unless the user asked.

---

## File map

| File | Role |
|------|------|
| Modify `ClickGuiModule.java` | Scale slider + getters |
| Modify `ClickGuiScreen.java` | GL scale + mouse inverse |
| Create `ClickGuiModuleTest.java` | Default/min/max/step smoke |

---

### Task 1: Failing test + Scale setting

**Files:**
- Create: `gnuclient recode/src/test/java/gnu/client/module/modules/settings/ClickGuiModuleTest.java`
- Modify: `gnuclient recode/src/main/java/gnu/client/module/modules/settings/ClickGuiModule.java`

- [ ] **Step 1: Write failing test**

```java
package gnu.client.module.modules.settings;

import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ClickGuiModuleTest {

    @Test
    public void scaleDefaults() {
        ClickGuiModule m = new ClickGuiModule();
        SliderSetting scale = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Scale".equals(s.getName()))
                scale = (SliderSetting) s;
        }
        assertNotNull(scale);
        assertEquals(1.0f, scale.getValue(), 0.001f);
        assertEquals(0.75f, scale.getMin(), 0.001f);
        assertEquals(1.50f, scale.getMax(), 0.001f);
        assertEquals(0.05f, scale.getStep(), 0.001f);
        assertEquals(1.0f, m.getScale(), 0.001f);
    }
}
```

- [ ] **Step 2: Run test — expect FAIL** (no Scale setting)

```bash
cd "gnuclient recode" && ./gradlew test --tests gnu.client.module.modules.settings.ClickGuiModuleTest --no-daemon
```

- [ ] **Step 3: Add setting to `ClickGuiModule`**

```java
private final SliderSetting scale = addSetting(
        new SliderSetting("Scale", 1.0f, 0.75f, 1.50f, 0.05f));

public SliderSetting getScaleSetting() {
    return scale;
}

public float getScale() {
    return scale.getValue();
}
```

Also add static fallback helper used by screen:

```java
public static float resolveScale() {
    ClickGuiModule gui = instance();
    return gui != null ? gui.getScale() : 1.0f;
}
```

- [ ] **Step 4: Re-run test — expect PASS**

---

### Task 2: Apply scale in `ClickGuiScreen`

**Files:**
- Modify: `gnuclient recode/src/main/java/gnu/client/ui/clickgui/ClickGuiScreen.java`

- [ ] **Step 1: Helpers**

```java
private float userScale() {
    float s = ClickGuiModule.resolveScale();
    return s <= 0f ? 1.0f : s;
}

/** Minecraft scaled-GUI mouse → logical coords under userScale from (0,0). */
private int logicalX(int mouseX) {
    return Math.round(mouseX / userScale());
}

private int logicalY(int mouseY) {
    return Math.round(mouseY / userScale());
}
```

- [ ] **Step 2: Wrap draw in GL scale**

In `drawScreen`, after computing `sr` / `scale` (keep `scale = sr.getScaleFactor()` for PixelAlign):

1. Convert mouse for column `update` / `mouseDragged` to logicalX/Y **before** GL (update uses mouse for hover).
2. Inside `UiKit.GlGuard.run`:
   - `GlStateManager.pushMatrix();`
   - `GlStateManager.scale(userScale(), userScale(), 1.0f);`
   - draw top bar with `sr.getScaledWidth() / userScale()` as logical screen width (so bar still fits visually), OR keep using `sr.getScaledWidth()` and accept bar math in scaled space — **preferred:** pass `logicalScreenW = sr.getScaledWidth() / userScale()` into `drawTopBar` so layout stays consistent with mouse.
   - `GlStateManager.popMatrix();` in finally before scissors clear ends.

Import: `net.minecraft.client.renderer.GlStateManager`

- [ ] **Step 3: Mouse paths use logical coords**

- `mouseClicked` / `mouseReleased` / `mouseClickMove` / `handleMouseInput`: use `logicalX(mouseX)`, `logicalY(mouseY)` for `searchHit` and column handlers.
- `searchHit`: compute bar geometry in **logical** space using `width / userScale()` (GuiScreen.width is scaled GUI width).

Concrete `searchHit`:

```java
private boolean searchHit(int mouseX, int mouseY) {
    float us = userScale();
    float screenW = width / us;
    float barW = Math.min(480f, screenW - 24f);
    float barX = (screenW - barW) * 0.5f;
    float searchW = Math.min(SEARCH_W, barW * 0.48f);
    float searchX = barX + (barW - searchW) * 0.5f;
    float searchY = TOP_BAR_Y + 7f;
    return mouseX >= searchX && mouseX <= searchX + searchW
            && mouseY >= searchY && mouseY <= searchY + 22f;
}
```

Callers pass already-logical mouse into `searchHit`.

- [ ] **Step 4: Compile + unit test**

```bash
cd "gnuclient recode" && ./gradlew test --tests gnu.client.module.modules.settings.ClickGuiModuleTest compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 5: Manual checklist (record pending if no client)**

Scale 0.75 / 1.0 / 1.5: open GUI, click modules, drag column, search focus, save/reopen keeps Scale.

---

## Spec coverage

| Spec | Task |
|------|------|
| Scale slider 0.75–1.50 step 0.05 default 1.0 | 1 |
| Persist via module config | 1 (automatic) |
| GL scale + inverse mouse | 2 |
| Layout positions logical | 2 |
| Null instance → 1.0 | 1 (`resolveScale`) |

## Placeholder scan

None.
