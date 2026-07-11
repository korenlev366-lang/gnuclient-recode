# OpenMiau Displace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port OpenMiau Displace into GNUClient as a Combat module (full feature set: STATIC/DYNAMIC, void find, delay, blink, arrow, has-knockback).

**Architecture:** Single `DisplaceModule` ported from OpenMiau `Displace.java`. PRE logic on `onTickStart` / PreUpdate path; silent rotation via `RotationState` priority **2**; strafe via `ClientEventListener` → `patchStrafe`; blink via `BlinkManager` + `BlinkModules.DISPLACE`; arrow via `onRender`.

**Tech Stack:** Java 8, Forge 1.8.9 (`gnuclient recode/`), PacketEvents, RotationState, BlinkManager, StrafeEvent.

**Spec:** `docs/superpowers/specs/2026-07-12-openmiau-displace-design.md`  
**Reference:** `/home/lev/linux minecraft thing/OpenMiau/src/main/java/myau/module/modules/combat/Displace.java`

**Commits:** Skip unless the user asked.

**Constant:** `DisplaceModule.ROTATION_PRIORITY = 2` (between KA=1 and Scaffold=3).

---

## File map

| File | Role |
|------|------|
| Create `DisplaceModule.java` | Full port |
| Create `DisplaceModuleTest.java` | Settings / visibleWhen / name / suffix |
| Modify `GnuClientMod.java` | `safeRegister(new DisplaceModule())` |
| Modify `ClientEventListener.java` | `DisplaceModule.patchStrafe(event)` after Velocity |
| Modify `MoveFixUtil.java` (optional) | Only if sharing `fixMovement`; prefer private method on module |

---

### Task 1: Settings smoke test + module skeleton

**Files:**
- Create: `gnuclient recode/src/test/java/gnu/client/module/modules/combat/DisplaceModuleTest.java`
- Create: `gnuclient recode/src/main/java/gnu/client/module/modules/combat/DisplaceModule.java` (settings + stubs)

- [ ] **Step 1: Write failing test**

```java
package gnu.client.module.modules.combat;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DisplaceModuleTest {

    @Test
    public void nameAndDefaultSettings() {
        DisplaceModule m = new DisplaceModule();
        assertEquals("Displace", m.getName());

        ModeSetting dynamic = null, direction = null;
        SliderSetting yaw = null, delay = null;
        BoolSetting findVoid = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Dynamic Angle".equals(s.getName())) dynamic = (ModeSetting) s;
            else if ("Direction".equals(s.getName())) direction = (ModeSetting) s;
            else if ("Yaw Offset".equals(s.getName())) yaw = (SliderSetting) s;
            else if ("Delay ms".equals(s.getName())) delay = (SliderSetting) s;
            else if ("Find Void".equals(s.getName())) findVoid = (BoolSetting) s;
        }
        assertNotNull(dynamic);
        assertEquals(0, dynamic.getIndex()); // STATIC
        assertEquals("STATIC", dynamic.getCurrentMode());
        assertNotNull(direction);
        assertEquals("LEFT", direction.getCurrentMode());
        assertNotNull(yaw);
        assertEquals(90.0f, yaw.getValue(), 0.001f);
        assertTrue(yaw.isVisible());
        assertNotNull(delay);
        assertEquals(0.0f, delay.getValue(), 0.001f);
        assertNotNull(findVoid);
        assertTrue(findVoid.isVisible());

        dynamic.setIndex(1); // DYNAMIC
        assertFalse(yaw.isVisible());
        assertFalse(direction.isVisible());
        assertFalse(findVoid.isVisible());
    }

    @Test
    public void suffixShowsDelayMs() {
        DisplaceModule m = new DisplaceModule();
        SliderSetting delay = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Delay ms".equals(s.getName())) delay = (SliderSetting) s;
        }
        assertNotNull(delay);
        delay.setValue(100.0f);
        String[] suffix = m.getSuffix();
        assertEquals(1, suffix.length);
        assertEquals("100ms", suffix[0]);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd "gnuclient recode" && ./gradlew test --tests gnu.client.module.modules.combat.DisplaceModuleTest --no-daemon
```

- [ ] **Step 3: Skeleton `DisplaceModule` with settings only**

```java
public final class DisplaceModule extends Module implements PacketListener {
    public static final int ROTATION_PRIORITY = 2;

    private final ModeSetting dynamicAngle = addSetting(new ModeSetting("Dynamic Angle", 0,
            java.util.Arrays.asList("STATIC", "DYNAMIC")));
    private final SliderSetting yawOffset = addSetting(new SliderSetting("Yaw Offset", 90f, 0f, 180f, 1f));
    private final SliderSetting delayMs = addSetting(new SliderSetting("Delay ms", 0f, 0f, 500f, 1f));
    private final ModeSetting direction = addSetting(new ModeSetting("Direction", 0,
            java.util.Arrays.asList("LEFT", "RIGHT")));
    private final BoolSetting showDirection = addSetting(new BoolSetting("Show Direction", true));
    private final BoolSetting findVoid = addSetting(new BoolSetting("Find Void", false));
    private final BoolSetting blink = addSetting(new BoolSetting("Blink", false));
    private final BoolSetting ignoreTeammates = addSetting(new BoolSetting("Ignore Teammates", true));
    private final BoolSetting hasKnockback = addSetting(new BoolSetting("Has Knockback", false));

    public DisplaceModule() {
        super("Displace", "Displace targets toward void while attacking", Category.COMBAT);
        yawOffset.visibleWhen(() -> dynamicAngle.getIndex() == 0);
        direction.visibleWhen(() -> dynamicAngle.getIndex() == 0);
        findVoid.visibleWhen(() -> dynamicAngle.getIndex() == 0);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{Math.round(delayMs.getValue()) + "ms"};
    }

    // stubs: onEnable/onDisable/onTickStart/onTick/onRender/onSend/onReceive/patchStrafe
}
```

Match ModeSetting API used elsewhere (`getIndex` / `setIndex` / `getCurrentMode`). If ModeSetting lacks `setIndex`, use whatever the codebase uses in other tests.

- [ ] **Step 4: Re-run test — PASS**

---

### Task 2: Port core logic from OpenMiau

**Files:**
- Modify: `DisplaceModule.java` (full behavior)
- Modify: `GnuClientMod.java`
- Modify: `ClientEventListener.java`

- [ ] **Step 1: Copy OpenMiau behavior**

Read reference file end-to-end. Port into `DisplaceModule`:

- All scan constants and static VOID_SCAN tables
- State fields (`displaceThisTick`, `active`, `hasKB`, blink/arrow maps, etc.)
- `findClosestTarget`, void/dynamic scorers, `isVoidColumn`, `hasBlockCollision` (use `addCollisionBoxesToList`)
- `shouldDisplaceInCurrentWindow` / delay ticks
- `fixMovement(float yaw)` — private static, OpenMiau `MoveUtil.fixMovement` body
- PRE tick logic in **`onTickStart()`** (same PRE slot as KillAura attack / before living): OpenMiau `onUpdate` PRE body
  - When displace tick: `RotationState.applyState(true, yaw, pitch, yaw, ROTATION_PRIORITY)` with current pitch from player; call `fixMovement(yaw)` on `player.movementInput`
  - When not displacing this tick / inactive: if priority==2, `RotationState.reset()` or `applyState(false,...)`
- Off-tick attack re-click: `KeyBinding.onTick(attack key)`
- `patchStrafe(StrafeEvent)` — OpenMiau `onStrafe`
- Blink: `onSend` if C03 + conditions → `BlinkManager.INSTANCE.setBlinkState(true, BlinkModules.DISPLACE)`; `onTick`/`onTickStart` release when `releaseBlinkNextTick`
- `onRender(partialTicks)` — arrow + fade (OpenMiau `onRender3D`)
- `onEnable`/`onDisable`: `resetState`; disable releases blink if owner DISPLACE
- Targeting: `RavenAntiBot.isBot`, `Utils.isTeammate` when ignore teammates, `Utils.isFriended` if present
- Attack held: `Mc.isAttackKeyHeld()` or physical attack bind

Do **not** invent new void heuristics — keep OpenMiau math.

- [ ] **Step 2: Register + wire**

`GnuClientMod`: `safeRegister(new DisplaceModule());`

`ClientEventListener.onStrafe`:

```java
VelocityModule.patchStrafe(event);
DisplaceModule.patchStrafe(event);
```

- [ ] **Step 3: Compile + unit tests**

```bash
cd "gnuclient recode" && ./gradlew test --tests gnu.client.module.modules.combat.DisplaceModuleTest compileJava --no-daemon
```

Expected: PASS / SUCCESS.

- [ ] **Step 4: Self-check rotation**

Confirm Displace only applies `RotationState` when `displaceThisTick` and active; clears when inactive so KA/Scaffold can take over.

---

### Task 3: Manual verification checklist

- [ ] Record automated: unit test + build PASS
- [ ] In-game (or note pending): STATIC L/R; Find Void; DYNAMIC; Blink; arrow; Has Knockback gate; no crash with KA on

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| Settings + visibleWhen | 1 |
| Full OpenMiau logic | 2 |
| BlinkManager DISPLACE | 2 |
| Strafe hook | 2 |
| Arrow render | 2 |
| Register module | 2 |
| Priority 2 | 2 |
| Tests | 1–2 |

## Placeholder scan

None — rotation hook fixed to `onTickStart` + `RotationState` priority 2.
