# OpenMiau MoreKB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a separate MoreKB combat module ported from OpenMiau, with mutual exclusion against W Tap.

**Architecture:** Single `MoreKBModule` class mirroring OpenMiau `MoreKB.java` hooks (attack, tick, C03 send, movement/key). Register in `GnuClientMod`. W Tap and MoreKB disable each other on enable.

**Tech Stack:** Java 8, Forge 1.8.9 (`gnuclient recode/`), PacketEvents, `Mc` packet helpers.

**Spec:** `docs/superpowers/specs/2026-07-11-openmiau-morekb-design.md`  
**Reference:** `/home/lev/linux minecraft thing/OpenMiau/src/main/java/myau/module/modules/combat/MoreKB.java`

**Commits:** Skip unless the user asked.

---

## File map

| File | Role |
|------|------|
| Create `src/main/java/gnu/client/module/modules/combat/MoreKBModule.java` | Full MoreKB port |
| Create `src/test/java/gnu/client/module/modules/combat/MoreKBModuleTest.java` | Modes / visibleWhen / name |
| Modify `GnuClientMod.java` | `safeRegister(new MoreKBModule())` |
| Modify `WTapModule.java` | On enable → disable MoreKB |
| Modify `ClientEventListener.java` | `noteForgeAttack` for MoreKB |
| Modify `CombatAttackNotify.java` | Same attack notify |
| Modify `MovementInputHook.java` | `MoreKBModule.patchMovementInput` for WTap mode |

---

### Task 1: Failing tests + `MoreKBModule` skeleton settings

**Files:**
- Create: `gnuclient recode/src/test/java/gnu/client/module/modules/combat/MoreKBModuleTest.java`
- Create: `gnuclient recode/src/main/java/gnu/client/module/modules/combat/MoreKBModule.java` (settings + stubs first if TDD)

- [ ] **Step 1: Write failing test**

```java
package gnu.client.module.modules.combat;

import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MoreKBModuleTest {

    @Test
    public void registersTenModesDefaultLegit() {
        MoreKBModule m = new MoreKBModule();
        ModeSetting mode = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
        }
        assertNotNull(mode);
        assertEquals(10, mode.getModes().size());
        assertEquals("Legit", mode.getCurrentMode());
        assertEquals(0, mode.getIndex());
        assertEquals("MoreKB", m.getName());
    }

    @Test
    public void spamSettingsVisibleOnlyOnSpamS() {
        MoreKBModule m = new MoreKBModule();
        ModeSetting mode = null;
        SliderSetting dist = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
            else if ("SpamS Distance".equals(s.getName()))
                dist = (SliderSetting) s;
        }
        assertNotNull(mode);
        assertNotNull(dist);
        mode.setValue(0);
        assertFalse(dist.isVisible());
        mode.setValue(9);
        assertTrue(dist.isVisible());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
cd "/home/lev/linux minecraft thing/gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests 'gnu.client.module.modules.combat.MoreKBModuleTest' --no-daemon -Dorg.gradle.jvmargs=-Xmx8g
```

- [ ] **Step 3: Implement `MoreKBModule` settings + empty lifecycle** so tests pass (full behavior in Task 2)

Mode names (display): `Legit`, `LegitFast`, `LessPacket`, `Packet`, `DoublePacket`, `WTap`, `SprintTap`, `Silent`, `SneakPacket`, `SpamS`.

Settings:
- `Mode` default 0
- `Intelligent` bool false
- `Only Ground` bool true
- `SpamS Distance` 3 (0–6), `SpamS Tick` 2 (0–10) — visibleWhen mode index == 9

```java
public final class MoreKBModule extends Module {
    // fields + constructor with visibleWhen
    public MoreKBModule() {
        super("MoreKB", "Increases knockback via sprint resets (OpenMiau)", Category.COMBAT);
        spamSDistance.visibleWhen(() -> mode.getIndex() == 9);
        spamSTick.visibleWhen(() -> mode.getIndex() == 9);
    }
}
```

- [ ] **Step 4: Re-run test — PASS**

---

### Task 2: Port OpenMiau behavior into `MoreKBModule`

**Files:**
- Modify: `MoreKBModule.java`

- [ ] **Step 1: Port logic from OpenMiau `MoreKB.java`**

Map:

| OpenMiau | GNUClient |
|----------|-----------|
| `onAttack` | `noteForgeAttack(Entity)` |
| `onTick` | `onTick()` |
| `onPacket` SEND C03 | `PacketListener.onSend` — return false; inject C0B via `Mc.addToSendQueue` / `Mc.sendSprintActionPacket` |
| WTap moveForward=0 | also set flag + `patchMovementInput` static |
| SpamS KeyBinding back | `KeyBinding.setKeyBindState`; clear on disable |
| `onlyGround` / `intelligent` | same gates |
| `addToSendQueue` C0B | `Mc.sendSprintActionPacket` / `Mc.sendSneakActionPacket` if present, else `Mc.addToSendQueue(new C0B...)` |

State fields: `target`, `ticks`, `spamSActiveTicks`, `wTapTicks` (match OpenMiau).

`onEnable`: clear state; `PacketEvents.register(this)`; disable W Tap:

```java
Module wTap = ModuleManager.INSTANCE.getModule("W Tap");
if (wTap != null && wTap.isEnabled())
    wTap.setEnabled(false);
```

`onDisable`: unregister; clear state; force back key false if SpamS used it.

Implement `isMoving()` as OpenMiau.

- [ ] **Step 2: Compile**

```bash
GRADLE_OPTS="-Xmx8g" ./gradlew compileJava --no-daemon -Dorg.gradle.jvmargs=-Xmx8g
```

Expected: SUCCESS.

---

### Task 3: Wire registration, exclusion, hooks

**Files:**
- Modify: `GnuClientMod.java` — register near W Tap
- Modify: `WTapModule.onEnable` — disable MoreKB
- Modify: `ClientEventListener.onAttackEntity` — MoreKB noteForgeAttack
- Modify: `CombatAttackNotify.noteAttack` — same
- Modify: `MovementInputHook` — `MoreKBModule.patchMovementInput(movInput)` after W Tap

- [ ] **Step 1: GnuClientMod**

```java
safeRegister(new MoreKBModule());
```

(import `MoreKBModule`)

- [ ] **Step 2: WTapModule.onEnable**

```java
Module moreKb = ModuleManager.INSTANCE.getModule("MoreKB");
if (moreKb != null && moreKb.isEnabled())
    moreKb.setEnabled(false);
```

- [ ] **Step 3: Attack notifies**

Same pattern as W Tap in `ClientEventListener` and `CombatAttackNotify`.

- [ ] **Step 4: MovementInputHook**

```java
MoreKBModule.patchMovementInput(movementInput);
```

`patchMovementInput` should zero `moveForward` when WTap mode active and `wTapTicks > 0` (OpenMiau also sets sprinting false in onTick — keep both).

- [ ] **Step 5: Compile + tests**

```bash
GRADLE_OPTS="-Xmx8g" ./gradlew test --tests 'gnu.client.module.modules.combat.MoreKBModuleTest' compileJava --no-daemon -Dorg.gradle.jvmargs=-Xmx8g
```

---

### Task 4: Build + checklist

- [ ] **Step 1: Full build**

```bash
cd "/home/lev/linux minecraft thing/gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test build --no-daemon -Dorg.gradle.jvmargs=-Xmx8g
```

- [ ] **Step 2: Grep**

```bash
rg -n "MoreKB|myau\\." src/main/java/gnu/client/module/modules/combat/MoreKBModule.java src/main/java/gnu/client/GnuClientMod.java
```

Expected: module present; no `myau.` imports.

- [ ] **Step 3: Manual checklist**

- MoreKB in Combat ClickGUI; 10 modes; SpamS shows distance/tick
- Enable MoreKB → W Tap off; enable W Tap → MoreKB off
- Disable clears forced S key

---

## Spec coverage

| Spec | Task |
|------|------|
| Separate MoreKB module | 1–2 |
| 10 OpenMiau modes | 2 |
| Mutual exclusion | 2–3 |
| Attack/tick/C03/move hooks | 2–3 |
| Register + build | 3–4 |
