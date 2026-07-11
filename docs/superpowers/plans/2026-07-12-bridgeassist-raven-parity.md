# BridgeAssist raven parity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite GNUClient Bridge Assist to match raven-bS `BridgeAssist` (input-time sneak, SimulatedPlayer edge, collision offset, ClientRotation pre-place).

**Architecture:** Port raven `SimulatedPlayer` into `gnu.client.utility.sim`, rewrite `BridgeAssistModule` to subscribe to `PrePlayerInputEvent` + `ClientRotationEvent` (Forge bus), remove tick/`forceSneak` edge path. Edge uses `getCollidingBoundingBoxes`.

**Tech Stack:** Java 8, Forge 1.8.9 (`gnuclient recode/`), Mixin accessors, existing `PrePlayerInputEvent` / `ClientRotationEvent` / `RotationUtils` / `BlockUtils`.

**Spec:** `docs/superpowers/specs/2026-07-12-bridgeassist-raven-parity-design.md`  
**Reference:** `/home/lev/linux minecraft thing/raven-bS/src/main/java/keystrokesmod/module/impl/player/BridgeAssist.java`  
**Sim reference:** `/home/lev/linux minecraft thing/raven-bS/src/main/java/keystrokesmod/script/model/SimulatedPlayer.java`

**Commits:** Skip unless the user asked.

---

## File map

| File | Role |
|------|------|
| Create `src/main/java/gnu/client/mixin/impl/accessors/IAccessorEntity.java` | `fire` / `nextStepDistance` / `isInWeb` for sim ctor |
| Modify `src/main/resources/mixins.gnuclient.json` | Register `IAccessorEntity` |
| Create `src/main/java/gnu/client/utility/sim/SimulatedPlayer.java` | Raven sim port (NoSlow stubbed) |
| Rewrite `src/main/java/gnu/client/module/modules/player/BridgeAssistModule.java` | Raven control flow |
| Modify `src/main/java/gnu/client/runtime/MovementInputHook.java` | Drop BA `patchMovementInput` call |
| Create `src/test/java/gnu/client/module/modules/player/BridgeAssistModuleTest.java` | Settings / name smoke |
| Create/update vault `gnu client dev/Decision - BridgeAssist raven parity.md` | Supersede predictive-edge note |

---

### Task 1: `IAccessorEntity` + mixin json

**Files:**
- Create: `gnuclient recode/src/main/java/gnu/client/mixin/impl/accessors/IAccessorEntity.java`
- Modify: `gnuclient recode/src/main/resources/mixins.gnuclient.json`

- [ ] **Step 1: Add accessor** (mirror raven)

```java
package gnu.client.mixin.impl.accessors;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface IAccessorEntity {
    @Accessor("fire")
    int getFire();

    @Accessor("nextStepDistance")
    int getNextStepDistance();

    @Accessor("isInWeb")
    boolean getIsInWeb();
}
```

- [ ] **Step 2: Register in `mixins.gnuclient.json`**

Add under mixins array (next to other accessors):

```json
"accessors.IAccessorEntity",
```

- [ ] **Step 3: Compile**

Run: `cd "gnuclient recode" && ./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL

---

### Task 2: Port `SimulatedPlayer`

**Files:**
- Create: `gnuclient recode/src/main/java/gnu/client/utility/sim/SimulatedPlayer.java`

- [ ] **Step 1: Copy raven file and adapt**

Source: `raven-bS/src/main/java/keystrokesmod/script/model/SimulatedPlayer.java` (~1284 lines).

Adaptations (do all):

1. Package → `gnu.client.utility.sim`
2. Imports:
   - `IAccessorEntity` / `IAccessorEntityLivingBase` → `gnu.client.mixin.impl.accessors.*`
   - Remove `keystrokesmod.module.ModuleManager` and `keystrokesmod.module.impl.movement.NoSlow`
3. NoSlow block (~line 253 in raven): treat as “no NoSlow” — use vanilla item-use slowdown only:

```java
// Was: ModuleManager.noSlow / NoSlow.getSlowed()
boolean useItemBlocksSprint = true;
float slowed = 0.2f; // unused when useItemBlocksSprint; keep local if branch needs it
```

Locate every `NoSlow.` / `ModuleManager.noSlow` reference and replace so item-use path matches “NoSlow disabled” (raven when module null/off).

4. Keep public API BridgeAssist needs:
   - `fromClientPlayer(MovementInput)`
   - field `movementInput` (mutable `.sneak`)
   - `tick()`
   - `getEntityBoundingBox()` (or `box` field if that is what raven returns — match raven method used by BridgeAssist)

5. `Minecraft.getMinecraft()` / MCP types stay as in raven (Forge named).

- [ ] **Step 2: Compile**

Run: `cd "gnuclient recode" && ./gradlew compileJava --no-daemon`  
Expected: BUILD SUCCESSFUL (fix any missing accessor/method).

---

### Task 3: Settings smoke test (failing then pass with rewrite)

**Files:**
- Create: `gnuclient recode/src/test/java/gnu/client/module/modules/player/BridgeAssistModuleTest.java`
- Modify: `BridgeAssistModule.java` (settings portion first OK)

- [ ] **Step 1: Write test**

```java
package gnu.client.module.modules.player;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class BridgeAssistModuleTest {

    @Test
    public void ravenDefaultSettings() {
        BridgeAssistModule m = new BridgeAssistModule();
        assertEquals("Bridge Assist", m.getName());

        SliderSetting edge = null, unsneak = null, jump = null;
        BoolSetting pre = null, sneakKey = null, blocks = null, look = null, noFwd = null;
        for (Setting<?> s : m.getSettings()) {
            switch (s.getName()) {
                case "Edge offset": edge = (SliderSetting) s; break;
                case "Unsneak delay": unsneak = (SliderSetting) s; break;
                case "Sneak on jump": jump = (SliderSetting) s; break;
                case "Pre place": pre = (BoolSetting) s; break;
                case "Sneak key pressed": sneakKey = (BoolSetting) s; break;
                case "Holding blocks": blocks = (BoolSetting) s; break;
                case "Looking down": look = (BoolSetting) s; break;
                case "Not moving forward": noFwd = (BoolSetting) s; break;
                default: break;
            }
        }
        assertNotNull(edge);
        assertNotNull(unsneak);
        assertNotNull(jump);
        assertNotNull(pre);
        assertNotNull(sneakKey);
        assertNotNull(blocks);
        assertNotNull(look);
        assertNotNull(noFwd);

        assertEquals(0.0f, edge.getValue(), 0.001f);
        assertEquals(0.0f, edge.getMin(), 0.001f);
        assertEquals(0.3f, edge.getMax(), 0.001f);
        assertEquals(0.01f, edge.getStep(), 0.001f);

        assertEquals(50.0f, unsneak.getValue(), 0.001f);
        assertEquals(50.0f, unsneak.getMin(), 0.001f);
        assertEquals(300.0f, unsneak.getMax(), 0.001f);

        assertEquals(0.0f, jump.getValue(), 0.001f);
        assertEquals(500.0f, jump.getMax(), 0.001f);

        assertFalse(pre.getValue());
        assertFalse(sneakKey.getValue());
        assertFalse(blocks.getValue());
        assertFalse(look.getValue());
        assertFalse(noFwd.getValue());
    }
}
```

- [ ] **Step 2: Run test (expect fail until settings renamed)**

Run: `cd "gnuclient recode" && ./gradlew test --tests gnu.client.module.modules.player.BridgeAssistModuleTest --no-daemon`  
Expected: FAIL on setting names / defaults until Task 4.

---

### Task 4: Rewrite `BridgeAssistModule` (raven port)

**Files:**
- Rewrite: `gnuclient recode/src/main/java/gnu/client/module/modules/player/BridgeAssistModule.java`
- Modify: `gnuclient recode/src/main/java/gnu/client/runtime/MovementInputHook.java`

- [ ] **Step 1: Replace module with raven-faithful implementation**

Requirements (port from raven `BridgeAssist.java`; adapt types to GNUClient):

**Settings** (exact names for test):

```java
private final BoolSetting prePlace = addSetting(new BoolSetting("Pre place", false));
private final SliderSetting edgeOffset = addSetting(new SliderSetting("Edge offset", 0.0f, 0.0f, 0.3f, 0.01f));
private final SliderSetting unsneakDelay = addSetting(new SliderSetting("Unsneak delay", 50.0f, 50.0f, 300.0f, 5.0f));
private final SliderSetting sneakOnJump = addSetting(new SliderSetting("Sneak on jump", 0.0f, 0.0f, 500.0f, 5.0f));
private final BoolSetting sneakKeyPressed = addSetting(new BoolSetting("Sneak key pressed", false));
private final BoolSetting holdingBlocks = addSetting(new BoolSetting("Holding blocks", false));
private final BoolSetting lookingDown = addSetting(new BoolSetting("Looking down", false));
private final BoolSetting notMovingForward = addSetting(new BoolSetting("Not moving forward", false));
```

**Lifecycle:**

```java
@Override
public void onEnable() {
    MinecraftForge.EVENT_BUS.register(this);
    PacketEvents.register(this); // if using PacketListener for C08; else handle C08 via @SubscribeEvent SendPacket if exists
    resetUnsneak();
    sneakingFromModule = false;
}

@Override
public void onDisable() {
    MinecraftForge.EVENT_BUS.unregister(this);
    PacketEvents.unregister(this); // if registered
    sneakingFromModule = false;
    resetUnsneak();
}
```

**State fields** (raven): `sneakingFromModule`, `placed`, `forceRelease`, `sneakJumpDelayTicks`, `sneakJumpStartTick`, `unsneakDelayTicks`, `unsneakStartTick`.

**`@SubscribeEvent onPrePlayerInput(PrePlayerInputEvent e)`** — port raven body:

- Return if `!Utils.nullCheck()` / no player / `mc.currentScreen != null` / `capabilities.isFlying`
- Manual sneak via `Mc.isSneakKeyHeld()` (raven `Utils.isBindDown(keyBindSneak)`)
- Same branches: require sneak, notMovingForward, lookingDown (pitch &lt; 70), holdingBlocks
- Jump + sneakOnJump arm using `player.ticksExisted`
- Sim:

```java
SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);
sim.movementInput.sneak = false;
sim.tick();
double offset = computeEdgeOffset(sim.getEntityBoundingBox());
```

- NaN / offset vs `edgeOffset` branches identical to raven
- Helpers `pressSneak` / `tryReleaseSneak` / `releaseSneak` / `repressSneak` / `clearSneak` / `resetUnsneak` — use `e.setSneak(...)`; for key repress use `Mc.setKeyBindState(settings.keyBindSneak, ...)`

**`computeEdgeOffset`** — copy raven (collision boxes), using `mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck)`.

**Pre-place `@SubscribeEvent onClientRotation(ClientRotationEvent e)`** — port raven:

- Gate on `prePlace`, nullCheck, screen, flying
- Skip BedAura override if no GNUClient equivalent
- Holding blocks; lookingDown / notMovingForward gates
- `findTarget` using `BlockUtils.replaceable`, `RotationUtils.rayCastBlock`, `EnumFacing` NORTH/SOUTH/EAST/WEST
- `float[] sm = RotationUtils.smoothRotation(baseYaw, basePitch, target.yaw, target.pitch, 15, 20f);`
- `e.setYaw(sm[0]); e.setPitch(sm[1]);`
- Base yaw/pitch from `e.yaw`/`e.pitch` nullable else `RotationUtils.serverRotations`

**C08:** PacketListener `onSend` — if block placement and direction ≠ 255 and `sneakingFromModule && sneakKeyPressed`, set `placed = true`. Use `PacketHelper` if it exposes direction; otherwise cast `C08PacketPlayerBlockPlacement`.

**Remove:** `onTick` edge logic, `forceSneak` / `suppressSneak` statics, `computePredictedAABB`, `isAirBlock` edge grid, `patchMovementInput` body.

**Keep:** `public static void patchMovementInput(Object)` as empty no-op **only if** hook still calls it; prefer removing the call (Step 2).

**getInfo:** optional — edge offset string like raven (nice-to-have; not required by test).

- [ ] **Step 2: Clean `MovementInputHook`**

```java
public static void afterUpdatePlayerMoveState(Object movementInput) {
    // BridgeAssist now uses PrePlayerInputEvent on Forge bus (before this hook).
    VelocityModule.patchMovementInput(movementInput);
    WTapModule.patchMovementInput(movementInput);
    MoreKBModule.patchMovementInput(movementInput);
    ScaffoldModule.patchMovementInput(movementInput);
    KillAuraModule.patchMovementInput(movementInput);
    ScriptManager.instance().patchMovementInput(movementInput);
    StasisModule.patchPlayerInput(movementInput);
}
```

Remove `BridgeAssistModule` import/call.

- [ ] **Step 3: Run unit test**

Run: `cd "gnuclient recode" && ./gradlew test --tests gnu.client.module.modules.player.BridgeAssistModuleTest --no-daemon`  
Expected: PASS

- [ ] **Step 4: Full compile / build**

Run: `cd "gnuclient recode" && ./gradlew build --no-daemon`  
Expected: BUILD SUCCESSFUL

---

### Task 5: Vault decision note

**Files:**
- Create: `gnu client dev/Decision - BridgeAssist raven parity.md`
- Modify: `gnu client dev/Decision - BridgeAssist predictive edge detection.md` (status → superseded)
- Modify: `gnu client dev/Home.md` or Architecture if BridgeAssist is listed — add link

- [ ] **Step 1: Write decision**

```markdown
# Decision - BridgeAssist raven parity

**Status:** current  
**Date:** 2026-07-12  
**Supersedes:** [[Decision - BridgeAssist predictive edge detection]]

## Problem

Approximate tick prediction + isAirBlock edge did not match raven bridging feel.

## Decision

Port raven BridgeAssist: PrePlayerInputEvent sneak, SimulatedPlayer (sneak=false tick), getCollidingBoundingBoxes edge offset, ClientRotationEvent pre-place.

Forge may use getCollidingBoundingBoxes (Rain JNI ban does not apply).

## Spec / plan

- `gnuclient recode/docs/superpowers/specs/2026-07-12-bridgeassist-raven-parity-design.md`
- `gnuclient recode/docs/superpowers/plans/2026-07-12-bridgeassist-raven-parity.md`
```

- [ ] **Step 2: Mark old decision superseded**

At top of predictive-edge note:

```markdown
**Status:** superseded 2026-07-12 by [[Decision - BridgeAssist raven parity]]
```

---

### Task 6: Manual verification checklist (agent records what was run)

- [ ] **Step 1: Record automated verification**

- `./gradlew test --tests ...BridgeAssistModuleTest` → PASS  
- `./gradlew build` → PASS  

- [ ] **Step 2: In-game (user or agent if client available)**

1. Edge offset 0.10 / 0.20 / 0.30 on 1-wide bridge — no walk-off; sneak at lip  
2. Unsneak delay holds then releases on center  
3. Sneak on jump &gt; 0 holds across jump  
4. All conditions off — still assists  
5. Pre place on + blocks — pitch eases to side place  

If client not run: note “in-game pending” in final report; do not claim playtest pass.

---

## Spec coverage check

| Spec item | Task |
|-----------|------|
| PrePlayerInput sneak | 4 |
| SimulatedPlayer | 2 |
| Collision edge offset | 4 |
| Settings / defaults | 3–4 |
| ClientRotation pre-place | 4 |
| C08 placed flag | 4 |
| Remove forceSneak path | 4 |
| MovementInputHook cleanup | 4 |
| Vault supersede | 5 |
| Item whitelist deferred | — (out of scope) |
| IAccessorEntity for sim | 1 |

## Placeholder / consistency scan

- Setting names locked to test strings.  
- `SimulatedPlayer` package `gnu.client.utility.sim` used in Task 2 and 4.  
- No TBD steps.
