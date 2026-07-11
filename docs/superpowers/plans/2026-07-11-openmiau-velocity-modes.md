# OpenMiau Velocity Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace GNUClient Velocity (Reduce/JumpReset/Intave) with all 26 OpenMiau Velocity modes via a `VelocityMode` strategy package, defaulting to **Standard**.

**Architecture:** `VelocityModule` holds settings + shared state and dispatches to `gnu.client.module.modules.combat.velocity.*` mode classes ported from `OpenMiau/.../combat/velocity/`. Packet cancel = `PacketListener` return `true`. OMDelay uses a local S12 delay queue (no OpenMiau DelayManager).

**Tech Stack:** Java 8, Forge 1.8.9 (`gnuclient recode/`), PacketEvents, existing `IAccessorS12PacketEntityVelocity`, JUnit.

**Spec:** `docs/superpowers/specs/2026-07-11-openmiau-velocity-modes-design.md`  
**Reference tree:** `/home/lev/linux minecraft thing/OpenMiau/` (not OpenMyau-Plus)

**Commits:** Only if the user asked for commits; otherwise skip commit steps.

---

## File map

| Path | Responsibility |
|------|----------------|
| Rewrite `.../combat/VelocityModule.java` | Settings, shared state, mode registry, dispatch |
| Create `.../combat/velocity/VelocityMode.java` | Abstract hooks |
| Create `.../combat/velocity/VelocityDelayQueue.java` | OMDelay local S12 hold/release |
| Create `.../combat/velocity/VelocityMove.java` | Minimal MoveUtil (speed/yaw) used by several modes |
| Create 26 `*Velocity.java` mode classes | Port from OpenMiau |
| Modify `ClientEventListener.java` | `noteAttack` → VelocityModule |
| Keep `MovementInputHook` → `VelocityModule.patchMovementInput` | Forward to active mode `onMoveInput` |
| Update `VisibleWhenTest.java` | Assert Standard default + visibleWhen |
| Create `VelocityModuleModesTest.java` | Mode count / default / getActiveMode |

### Mode list (exact order — default Standard = index **7**)

```
OMDelay, Reverse, LegitTest, LegitSmart, IntaveReduce, Grimtest, JumpReset,
Standard, AAC, Bounce, BufferAbuse, Delay, Grim, GrimReduce, Ground, Intave,
Karhu, Legit, MMC, Matrix, Redesky, Tick, UniversoCraft, Vulcan, WatchdogPrediction, Watchdog
```

---

### Task 1: Scaffold `VelocityMode` + delay queue + move helper + failing tests

**Files:**
- Create: `gnuclient recode/src/main/java/gnu/client/module/modules/combat/velocity/VelocityMode.java`
- Create: `gnuclient recode/src/main/java/gnu/client/module/modules/combat/velocity/VelocityDelayQueue.java`
- Create: `gnuclient recode/src/main/java/gnu/client/module/modules/combat/velocity/VelocityMove.java`
- Create: `gnuclient recode/src/test/java/gnu/client/module/modules/combat/VelocityModuleModesTest.java`

- [ ] **Step 1: Write failing mode-registry test**

```java
package gnu.client.module.modules.combat;

import gnu.client.module.modules.combat.velocity.VelocityMode;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VelocityModuleModesTest {

    @Test
    public void registersTwentySixModesAndDefaultsToStandard() {
        VelocityModule v = new VelocityModule();
        ModeSetting mode = null;
        for (Setting<?> s : v.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
        }
        assertNotNull(mode);
        assertEquals(26, mode.getModes().size());
        assertEquals("Standard", mode.getCurrentMode());
        assertEquals(7, mode.getIndex());

        VelocityMode active = v.getActiveMode();
        assertNotNull(active);
        assertEquals("Standard", active.getName());
    }

    @Test
    public void standardSettingsVisibleOnlyOnStandardFamily() {
        VelocityModule v = new VelocityModule();
        ModeSetting mode = null;
        Setting<?> horizontal = null;
        for (Setting<?> s : v.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
            else if ("Horizontal".equals(s.getName()) || "horizontal".equals(s.getName()))
                horizontal = s;
        }
        assertNotNull(mode);
        assertNotNull(horizontal);

        mode.setValue(7); // Standard
        assertTrue(horizontal.isVisible());

        mode.setValue(6); // JumpReset
        assertTrue(!horizontal.isVisible());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL** (old VelocityModule or missing API)

```bash
cd "/home/lev/linux minecraft thing/gnuclient recode" && GRADLE_OPTS="-Xmx6g" ./gradlew test --tests 'gnu.client.module.modules.combat.VelocityModuleModesTest' -Dorg.gradle.jvmargs=-Xmx6g
```

- [ ] **Step 3: Add `VelocityMode`**

```java
package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovementInput;

/**
 * OpenMiau-style Velocity strategy. Hooks are no-ops by default.
 * Packet cancel: return true from {@link #onReceive}/{@link #onSend}.
 */
public abstract class VelocityMode {

    protected static final Minecraft mc = Minecraft.getMinecraft();

    protected final String name;
    protected final VelocityModule parent;

    protected VelocityMode(String name, VelocityModule parent) {
        this.name = name;
        this.parent = parent;
    }

    public final String getName() {
        return name;
    }

    public void onEnable() {}
    public void onDisable() {}

    /** @param pre true = early tick / PRE; false = late tick / POST */
    public void onUpdate(boolean pre) {}

    /** @return true to cancel receive */
    public boolean onReceive(Object packet) { return false; }

    /** @return true to cancel send */
    public boolean onSend(Object packet) { return false; }

    public void onAttack(Object target) {}

    public void onMoveInput(MovementInput input) {}
}
```

- [ ] **Step 4: Add `VelocityDelayQueue`**

```java
package gnu.client.module.modules.combat.velocity;

import gnu.client.runtime.packet.PacketUtil;

import java.util.ArrayDeque;
import java.util.Deque;

/** Local S12 hold for OMDelay — not Blink/Lagrange. */
public final class VelocityDelayQueue {

    private final Deque<Object> held = new ArrayDeque<>();
    private boolean delaying;
    private long delayStartTick = -1L;

    public void clear() {
        held.clear();
        delaying = false;
        delayStartTick = -1L;
    }

    public boolean isDelaying() {
        return delaying;
    }

    public void startDelay(long tickNow) {
        delaying = true;
        delayStartTick = tickNow;
    }

    public void stopDelayAndFlush() {
        delaying = false;
        delayStartTick = -1L;
        while (!held.isEmpty()) {
            Object pkt = held.pollFirst();
            if (pkt != null)
                PacketUtil.receivePacket(pkt); // if no such API, use the project's inbound re-inject helper; see PacketUtil / Mc for existing "process delayed inbound" pattern and match it
        }
    }

    public long ticksHeld(long tickNow) {
        if (!delaying || delayStartTick < 0L)
            return 0L;
        return Math.max(0L, tickNow - delayStartTick);
    }

    public void offer(Object packet) {
        held.addLast(packet);
    }
}
```

**Important:** Before implementing `stopDelayAndFlush`, grep `PacketUtil` / inbound delay patterns in `gnuclient recode`. If there is no `receivePacket`, flush by applying S12 motion to the player manually (motionX/Y/Z from packet / 8000) and do **not** invent a broken call. Document the chosen flush approach in a one-line comment on the method.

- [ ] **Step 5: Add `VelocityMove`**

```java
package gnu.client.module.modules.combat.velocity;

import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

public final class VelocityMove {

    private VelocityMove() {}

    public static double getSpeed() {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return 0.0;
        return Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
    }

    public static float getMoveYaw() {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return 0f;
        return p.rotationYaw;
    }

    public static void setSpeed(double speed, float yaw) {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return;
        float rad = (float) Math.toRadians(yaw);
        p.motionX = -Math.sin(rad) * speed;
        p.motionZ = Math.cos(rad) * speed;
    }

    public static boolean isMoving() {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return false;
        return p.moveForward != 0f || p.moveStrafing != 0f;
    }
}
```

- [ ] **Step 6: Do not rewrite VelocityModule yet** — Task 2. Leave tests failing until Task 2 lands.

---

### Task 2: Rewrite `VelocityModule` shell (settings + dispatch + Standard stub)

**Files:**
- Rewrite: `gnuclient recode/src/main/java/gnu/client/module/modules/combat/VelocityModule.java`
- Create: `.../velocity/StandardVelocity.java` (full port of OpenMiau Standard)
- Create stub mode classes for the other 25 names that only implement `getName` via constructor (empty hooks) — **or** register only Standard first and add stubs that extend VelocityMode with empty bodies so `getActiveMode` never NPE. Prefer **register all 26** with stubs so mode dropdown is complete; flesh out in later tasks.
- Update: `VisibleWhenTest.java` (Velocity assertions for new setting names)

- [ ] **Step 1: Port `StandardVelocity` from OpenMiau**

Source: `OpenMiau/.../velocity/StandardVelocity.java`

Adapt:
- `PacketEvent` RECEIVE → `onReceive(Object packet)` return true to cancel
- `parent.horizontal.getValue()` → GNUClient slider (0–100)
- Use `IAccessorS12PacketEntityVelocity` from `gnu.client.mixin.impl.accessors`
- `parent.onSwing` → BoolSetting on parent
- Use `Mc.player()` instead of only `mc.thePlayer` where null-safe helpers exist; `mc.thePlayer` OK if consistent with other modules

Full adapted body:

```java
package gnu.client.module.modules.combat.velocity;

import gnu.client.mixin.impl.accessors.IAccessorS12PacketEntityVelocity;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketHelper;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class StandardVelocity extends VelocityMode {

    public StandardVelocity(VelocityModule parent) {
        super("Standard", parent);
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return false;
        if (!PacketHelper.isEntityVelocity(packet))
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;
        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        double horizontal = parent.horizontal.getValue();
        double vertical = parent.vertical.getValue();

        if (horizontal == 0.0) {
            if (vertical != 0.0)
                player.motionY = vel.getMotionY() / 8000.0D;
            return true; // cancel packet
        }

        IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) vel;
        accessor.setMotionX((int) (vel.getMotionX() * horizontal / 100.0));
        accessor.setMotionY((int) (vel.getMotionY() * vertical / 100.0));
        accessor.setMotionZ((int) (vel.getMotionZ() * horizontal / 100.0));
        return false;
    }
}
```

- [ ] **Step 2: Rewrite `VelocityModule`**

Must include:
- `ModeSetting` with the 26 names; **default index 7 (Standard)**
- Settings matching OpenMiau (names can be Title Case for ClickGUI):
  - `Delay Ticks`, `Delay Chance` → OMDelay
  - `Legit Smart Jump Limit` → LegitSmart
  - `Intave Reduce Factor`, `Intave Reduce Hurt Time` → IntaveReduce
  - `Chance` → Legit / LegitTest / LegitSmart
  - `Horizontal`, `Vertical` → Standard / BufferAbuse / Redesky / Vulcan
  - `Explosions Horizontal`, `Explosions Vertical` → Standard
  - `Grim Reduce Jump Limit` → Grimtest
  - `Fake Check`, `Debug Log`, `On Swing` → always visible (as OpenMiau)
- Public fields or package accessors for shared state used by modes (`hasReceivedVelocity`, counters, `delayQueue`, etc.)
- `List<VelocityMode> modes` registered in constructor (Standard real; others stubs named correctly until later tasks)
- `getActiveMode()`, `modeName()` helper (`mode.getCurrentMode()`)
- `visibleWhen` predicates using `mode.getCurrentMode().equals("...")`
- `onEnable`/`onDisable`: register PacketEvents; call active mode enable/disable; clear delay queue; reset timer via `Mc.setTimerSpeed(1f)` on disable
- `onTick` / `onTickStart`: dispatch `onUpdate(true)` then `onUpdate(false)` (or tickStart=pre, tick=post — pick one and stick to it; document in class Javadoc)
- `onReceive`/`onSend`: dispatch to active mode
- `noteAttack(Object target)` for ClientEventListener
- `patchMovementInput`: dispatch `onMoveInput`
- `reducesKnockback()`: `isEnabled()` (OpenMiau always reduces when on)
- `isInLiquidOrWeb()`, `canDelay()` helpers ported from OpenMiau (KillAura autoblock check via existing KillAuraModule API if available; else `canDelay` = onGround only)

Stub example:

```java
public final class JumpResetVelocity extends VelocityMode {
    public JumpResetVelocity(VelocityModule parent) {
        super("JumpReset", parent);
    }
}
```

- [ ] **Step 3: Fix `VisibleWhenTest`**

Replace Velocity-specific assertions to use Horizontal + Mode Standard vs JumpReset (or delete Velocity section and rely on `VelocityModuleModesTest`).

- [ ] **Step 4: Compile + run tests**

```bash
cd "/home/lev/linux minecraft thing/gnuclient recode" && GRADLE_OPTS="-Xmx6g" ./gradlew test --tests 'gnu.client.module.modules.combat.VelocityModuleModesTest' --tests 'gnu.client.module.setting.VisibleWhenTest' -Dorg.gradle.jvmargs=-Xmx6g
```

Expected: PASS.

---

### Task 3: Port OM legacy modes (OMDelay → JumpReset)

**Files:** Create/fill under `.../combat/velocity/`:
- `OMDelayVelocity.java`
- `ReverseVelocity.java`
- `LegitTestVelocity.java`
- `LegitSmartVelocity.java`
- `IntaveReduceVelocity.java`
- `GrimTestVelocity.java`
- `JumpResetVelocity.java`

- [ ] **Step 1: For each file, copy from OpenMiau counterpart and adapt**

Transform rules (apply to every mode in this and later tasks):

1. Package → `gnu.client.module.modules.combat.velocity`
2. Extends `VelocityMode`; constructor `(VelocityModule parent)` calling `super("ExactName", parent)`
3. `UpdateEvent` PRE/POST → `onUpdate(boolean pre)` with `if (pre) { ... } else { ... }`
4. `PacketEvent` RECEIVE + `setCancelled` → `onReceive` return `true`
5. `PacketEvent` SEND → `onSend` return `true`
6. `AttackEvent` → `onAttack`
7. `MoveUtil.*` → `VelocityMove.*`
8. `RandomUtil` → `java.util.concurrent.ThreadLocalRandom` or `parent.random`
9. `PacketUtil.sendPacket` → `Mc.addToSendQueue` / existing `PacketUtil.sendPacket` in gnuclient (prefer send-queue when Blink must see packets)
10. `Myau.delayManager` → `parent.delayQueue` API from Task 1
11. `LongJump` checks → treat as disabled (`false`)
12. `KillAura` → `ModuleManager` / `KillAuraModule` for `canDelay` only
13. Mixins → gnuclient accessors (`IAccessorS12...`, timer via `Mc`)
14. Do not call OpenMiau `Myau.*`

- [ ] **Step 2: Replace stubs in `VelocityModule` registry with real classes**

- [ ] **Step 3: Compile**

```bash
GRADLE_OPTS="-Xmx6g" ./gradlew compileJava -Dorg.gradle.jvmargs=-Xmx6g
```

Expected: SUCCESS.

---

### Task 4: Port Rise batch A (AAC → Intave)

**Files:** `AACVelocity`, `BounceVelocity`, `BufferAbuseVelocity`, `DelayVelocity`, `GrimVelocity`, `GrimReduceVelocity`, `GroundVelocity`, `IntaveVelocity`

Same transform rules as Task 3. Replace stubs; compile.

---

### Task 5: Port Rise batch B (Karhu → Watchdog)

**Files:** `KarhuVelocity`, `LegitVelocity`, `MMCVelocity`, `MatrixVelocity`, `RedeskyVelocity`, `TickVelocity`, `UniversoCraftVelocity`, `VulcanVelocity`, `WatchdogPredictionVelocity`, `WatchdogVelocity`

Same transform rules. For Watchdog packet queues: keep **mode-local** `ArrayList` buffers; flush with the same inbound re-apply approach chosen for `VelocityDelayQueue`. Never push into Blink/Lagrange queues.

Compile after all registered.

---

### Task 6: Wire attack + movement + explosion settings for Standard

**Files:**
- Modify: `ClientEventListener.java` — in `onAttackEntity`, if Velocity enabled call `noteAttack(event.target)`
- Confirm: `MovementInputHook` still calls `VelocityModule.patchMovementInput`
- Ensure Standard handles explosion H/V if OpenMiau Standard does (check OpenMiau — if explosions only on parent settings for Standard, add S27 handling in `StandardVelocity` or parent dispatch). OpenMiau `StandardVelocity` currently only touches S12; explosion percents may be unused in that class — still keep settings for parity. If another mode uses them, wire there.

- [ ] **Step 1: Add to `ClientEventListener.onAttackEntity`**

```java
Module velocity = ModuleManager.INSTANCE.getModule("Velocity");
if (velocity instanceof VelocityModule && velocity.isEnabled())
    ((VelocityModule) velocity).noteAttack(event.target);
```

- [ ] **Step 2: Implement `VelocityModule.noteAttack`**

```java
public void noteAttack(Object target) {
    if (!isEnabled())
        return;
    getActiveMode().onAttack(target);
}
```

- [ ] **Step 3: Compile**

---

### Task 7: Build jar + checklist

- [ ] **Step 1: Full build**

```bash
cd "/home/lev/linux minecraft thing/gnuclient recode" && GRADLE_OPTS="-Xmx6g" ./gradlew test build -Dorg.gradle.jvmargs=-Xmx6g
```

Expected: `build/libs/gnuclient-1.2.0.jar` (or current version) SUCCESS; `VelocityModuleModesTest` PASS.

- [ ] **Step 2: Grep leftovers**

```bash
rg -n "Reduce|JumpReset mode|Intave Factor|mode.getValue\\(\\) == 0" src/main/java/gnu/client/module/modules/combat/VelocityModule.java || true
rg -n "myau\\.|OpenMyau|DelayModules|LongJump" src/main/java/gnu/client/module/modules/combat/velocity/ || true
```

Expected: no OpenMiau imports; no old Reduce-only logic left in VelocityModule.

- [ ] **Step 3: In-game checklist**

- Mode dropdown has 26 entries; default Standard
- Standard Horizontal 0 cancels KB packet / applies vertical only
- JumpReset jumps on hurt when grounded
- Disable Velocity restores timer to 1.0 if a mode changed it
- Blink/Lagrange still function with Velocity off

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| Replace modes with OpenMiau 26 | 2–5 |
| Default Standard (index 7) | 2 + test |
| VelocityMode package | 1–5 |
| visibleWhen settings | 2 |
| OMDelay local queue | 1, 3 |
| Attack / move hooks | 2, 6 |
| No OpenMyau-Plus | all |
| Verification | 7 |

## Self-review notes

- No TBD placeholders for API shapes; flush method must be resolved against `PacketUtil` at Task 1 Step 4.
- Stub-then-fill avoids a single 2k-line Task while keeping the dropdown complete early.
- `VisibleWhenTest` must be updated in Task 2 or it will fail CI.
