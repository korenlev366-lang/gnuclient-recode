# KillAura Autoblock + NoSlow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port OpenMyau KillAura autoblock modes onto KillAura (default NONE), add NoSlow with wired FloatManager, and add a shared BlinkManager with BlinkModules ownership for AUTO_BLOCK / reserved NO_SLOW.

**Architecture:** Settings live on `KillAuraModule`; mode logic in `KillAuraAutoBlock`. Shared `BlinkManager` (PacketListener) owns outbound holds for blink autoblock modes. `NoSlowModule` (PLAYER) skips use-item slow via mixin and drives `FloatManager.setFloatState(NO_SLOW)`. Hypixel stopBlock C09 flick runs only when NoSlow is enabled. Existing Grim guards stay untouched.

**Tech Stack:** Java 8, Forge 1.8.9, Sponge Mixin, PacketEvents / PacketUtil, Module settings, JUnit 4 under `src/test/java`.

**Spec:** `docs/superpowers/specs/2026-07-11-killaura-autoblock-noslow-design.md`  
**References:** `OpenMyau-Plus/.../KillAura.java` (autoblock switch ~538+), `NoSlow.java`, `FloatManager.java`, `BlinkManager.java`

---

## File map

| Path | Responsibility |
|------|----------------|
| `src/main/java/gnu/client/runtime/BlinkModules.java` | Ownership enum (OpenMyau parity) |
| `src/main/java/gnu/client/runtime/BlinkManager.java` | Shared outbound blink queue + PacketListener |
| `src/main/java/gnu/client/runtime/FloatModules.java` | `NO_SLOW` |
| `src/main/java/gnu/client/runtime/FloatManager.java` | Falling-edge +0.001 Y nudge |
| `src/main/java/gnu/client/module/modules/player/NoSlowModule.java` | Settings + float wiring + activity API |
| `src/main/java/gnu/client/mixin/impl/entity/MixinEntityPlayerSPNoSlow.java` | `isUsingItem` redirect in `onLivingUpdate` |
| `src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java` | Mode switch, start/stop/interact, blinkReset |
| `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java` | Settings + hook helper in `preUpdate` / disable |
| `src/main/java/gnu/client/runtime/mc/Mc.java` | Small helpers if missing (C08 use-item placement) |
| `src/main/java/gnu/client/GnuClientMod.java` | Register NoSlow + init managers |
| `src/main/resources/mixins.gnuclient.json` | NoSlow mixin entry |
| `src/test/java/gnu/client/runtime/BlinkManagerTest.java` | Ownership / offer rules |
| `src/test/java/gnu/client/runtime/FloatManagerLogicTest.java` | Falling predicate |
| `src/test/java/gnu/client/module/modules/player/NoSlowModeTest.java` | Mode index / multiplier GRIM alternation |
| Vault `gnu client/Decision - *.md` | Architecture log |

**Category note:** GNUClient has no `MOVEMENT` category — put NoSlow in `Category.PLAYER` (same as Sprint).

**Default lock:** Auto-block mode index **0 = NONE** (not OpenMyau’s HYPIXEL default).

---

### Task 1: BlinkModules + BlinkManager + unit tests

**Files:**
- Create: `src/main/java/gnu/client/runtime/BlinkModules.java`
- Create: `src/main/java/gnu/client/runtime/BlinkManager.java`
- Create: `src/test/java/gnu/client/runtime/BlinkManagerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package gnu.client.runtime;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlinkManagerTest {
    private BlinkManager mgr;

    @Before
    public void setUp() {
        mgr = new BlinkManager();
    }

    @Test
    public void releaseWrongOwnerReturnsFalseAndKeepsOwner() {
        assertTrue(mgr.setBlinkState(true, BlinkModules.AUTO_BLOCK));
        assertEquals(BlinkModules.AUTO_BLOCK, mgr.getBlinkingModule());
        assertFalse(mgr.setBlinkState(false, BlinkModules.NO_SLOW));
        assertEquals(BlinkModules.AUTO_BLOCK, mgr.getBlinkingModule());
        assertTrue(mgr.isBlinking());
    }

    @Test
    public void releaseCorrectOwnerClears() {
        mgr.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        assertTrue(mgr.setBlinkState(false, BlinkModules.AUTO_BLOCK));
        assertEquals(BlinkModules.NONE, mgr.getBlinkingModule());
        assertFalse(mgr.isBlinking());
    }

    @Test
    public void noneModuleRejected() {
        assertFalse(mgr.setBlinkState(true, BlinkModules.NONE));
    }

    @Test
    public void offerIgnoredWhenNotBlinking() {
        assertFalse(mgr.offerPacket(new Object()));
        assertEquals(0, mgr.queuedCount());
    }

    @Test
    public void offerQueuesWhenBlinking() {
        mgr.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        assertTrue(mgr.offerPacket(new Object()));
        assertEquals(1, mgr.queuedCount());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
cd "gnuclient recode" && ./gradlew test --tests gnu.client.runtime.BlinkManagerTest
```

Expected: FAIL (classes missing)

- [ ] **Step 3: Implement enum + manager**

`BlinkModules.java`:

```java
package gnu.client.runtime;

public enum BlinkModules {
    NONE,
    ANTI_VOID,
    AUTO_BLOCK,
    BLINK,
    DISPLACE,
    NO_FALL,
    NO_SLOW
}
```

`BlinkManager.java` (OpenMyau-shaped; flush via callback so tests need no Minecraft):

```java
package gnu.client.runtime;

import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.PacketUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Shared outbound blink ownership (OpenMyau BlinkManager).
 * AUTO_BLOCK used by KillAura blink modes; NO_SLOW reserved (do not activate this pass).
 */
public final class BlinkManager implements PacketListener {
    public static final BlinkManager INSTANCE = new BlinkManager();

    private BlinkModules blinkModule = BlinkModules.NONE;
    private boolean blinking;
    private final Deque<Object> blinkedPackets = new ArrayDeque<>();
    private Consumer<Object> flushSender = PacketUtil::sendPacketReleased;

    /** Package/test hook. */
    void setFlushSender(Consumer<Object> sender) {
        this.flushSender = sender != null ? sender : PacketUtil::sendPacketReleased;
    }

    public boolean offerPacket(Object packet) {
        if (blinkModule == BlinkModules.NONE || packet == null)
            return false;
        if (PacketHelper.isBlinkOutboundExempt(packet))
            return false;
        // OpenMyau: empty queue + C0F → do not hold
        if (blinkedPackets.isEmpty() && PacketHelper.isConfirmTransaction(packet))
            return false;
        blinkedPackets.offer(packet);
        return true;
    }

    public boolean setBlinkState(boolean state, BlinkModules module) {
        if (module == null || module == BlinkModules.NONE)
            return false;
        if (state) {
            blinkModule = module;
            blinking = true;
            return true;
        }
        if (blinkModule != module)
            return false;
        blinking = false;
        while (!blinkedPackets.isEmpty()) {
            Object p = blinkedPackets.poll();
            if (p != null)
                flushSender.accept(p);
        }
        blinkModule = BlinkModules.NONE;
        return true;
    }

    public BlinkModules getBlinkingModule() { return blinkModule; }
    public boolean isBlinking() { return blinking; }
    public int queuedCount() { return blinkedPackets.size(); }

    @Override
    public boolean onSend(Object packet) {
        if (PacketUtil.isDispatching() || PacketUtil.consumeFastTrack(packet))
            return false;
        return offerPacket(packet);
    }

    @Override
    public boolean onReceive(Object packet) { return false; }

    /** Above typical module listeners so AUTO_BLOCK wins when active. */
    @Override
    public int sendPriority() { return 100; }
}
```

If `PacketHelper.isConfirmTransaction` does not exist, add:

```java
public static boolean isConfirmTransaction(Object packet) {
    return packet instanceof net.minecraft.network.play.client.C0FPacketConfirmTransaction
            || classNameContains(packet, "C0FPacketConfirmTransaction");
}
```

For unit tests that call `offerPacket(new Object())`, exempt checks must not NPE — ensure `isBlinkOutboundExempt` / `isConfirmTransaction` return false for unknown objects (they already do via instanceof).

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew test --tests gnu.client.runtime.BlinkManagerTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gnu/client/runtime/BlinkModules.java \
  src/main/java/gnu/client/runtime/BlinkManager.java \
  src/test/java/gnu/client/runtime/BlinkManagerTest.java \
  src/main/java/gnu/client/runtime/packet/PacketHelper.java
git commit -m "Add shared BlinkManager with OpenMyau BlinkModules ownership."
```

---

### Task 2: Register BlinkManager at mod init

**Files:**
- Modify: `src/main/java/gnu/client/GnuClientMod.java`

- [ ] **Step 1: Register PacketListener once at client init**

In `GnuClientMod` after PacketEvents / Forge bus setup (same place `ClientEventListener` is registered), add:

```java
gnu.client.runtime.packet.PacketEvents.register(gnu.client.runtime.BlinkManager.INSTANCE);
```

Do **not** require the user `BlinkModule` to be on. Document in a one-line comment: coexistence — when user Blink is also on, both may hold; prefer not combining with KA blink modes.

- [ ] **Step 2: Build compile check**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gnu/client/GnuClientMod.java
git commit -m "Register BlinkManager on PacketEvents at mod init."
```

---

### Task 3: FloatModules + FloatManager logic + tests

**Files:**
- Create: `src/main/java/gnu/client/runtime/FloatModules.java`
- Create: `src/main/java/gnu/client/runtime/FloatManager.java`
- Create: `src/test/java/gnu/client/runtime/FloatManagerLogicTest.java`

- [ ] **Step 1: Failing test for pure falling predicate**

```java
package gnu.client.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class FloatManagerLogicTest {
    @Test
    public void fallingWhenOnGroundDescending() {
        assertTrue(FloatManager.isFallingEdge(true, 1.0, 1.1, -0.1));
    }

    @Test
    public void notFallingWhenAirborne() {
        assertFalse(FloatManager.isFallingEdge(false, 1.0, 1.1, -0.1));
    }

    @Test
    public void notFallingWhenRising() {
        assertFalse(FloatManager.isFallingEdge(true, 1.1, 1.0, 0.1));
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew test --tests gnu.client.runtime.FloatManagerLogicTest
```

- [ ] **Step 3: Implement**

```java
package gnu.client.runtime;

public enum FloatModules {
    NO_SLOW
}
```

```java
package gnu.client.runtime;

import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import gnu.client.event.PreUpdateEvent;

import java.util.LinkedHashMap;

public final class FloatManager {
    public static final FloatManager INSTANCE = new FloatManager();

    private final LinkedHashMap<FloatModules, Boolean> activeMap = new LinkedHashMap<>();
    private boolean floating;

    private FloatManager() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    /** OpenMyau FloatManager.isFalling — extracted for tests. */
    public static boolean isFallingEdge(boolean onGround, double posY, double lastTickPosY, double motionY) {
        return onGround && (posY - lastTickPosY) < 0.0 && motionY < 0.0;
    }

    public boolean isPredicted() { return floating; }

    public boolean hasActiveModule() {
        return activeMap.containsValue(Boolean.TRUE);
    }

    public void setFloatState(boolean state, FloatModules module) {
        if (module != null)
            activeMap.put(module, state);
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        EntityPlayerSP p = Mc.player();
        if (p == null) {
            floating = false;
            return;
        }
        if ((hasActiveModule() || isPredicted())
                && isFallingEdge(p.onGround, p.posY, p.lastTickPosY, p.motionY)) {
            p.setPosition(p.posX, p.posY + 0.001, p.posZ);
            floating = true;
        } else {
            floating = false;
        }
    }
}
```

- [ ] **Step 4: Run tests — PASS**

```bash
./gradlew test --tests gnu.client.runtime.FloatManagerLogicTest
```

- [ ] **Step 5: Call `FloatManager.init()` from `GnuClientMod` next to BlinkManager register; commit**

```bash
git add src/main/java/gnu/client/runtime/FloatModules.java \
  src/main/java/gnu/client/runtime/FloatManager.java \
  src/test/java/gnu/client/runtime/FloatManagerLogicTest.java \
  src/main/java/gnu/client/GnuClientMod.java
git commit -m "Add FloatManager with NO_SLOW float-module support."
```

---

### Task 4: NoSlowModule + mode tests

**Files:**
- Create: `src/main/java/gnu/client/module/modules/player/NoSlowModule.java`
- Create: `src/test/java/gnu/client/module/modules/player/NoSlowModeTest.java`
- Modify: `src/main/java/gnu/client/GnuClientMod.java` — `safeRegister(new NoSlowModule());`

- [ ] **Step 1: Failing test for GRIM multiplier alternation**

```java
package gnu.client.module.modules.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class NoSlowModeTest {
    @Test
    public void grimAlternates100Then20() {
        NoSlowModule.MotionCounter c = new NoSlowModule.MotionCounter();
        assertEquals(100, NoSlowModule.grimMotionPercent(c));
        assertEquals(20, NoSlowModule.grimMotionPercent(c));
        assertEquals(100, NoSlowModule.grimMotionPercent(c));
    }

    @Test
    public void modeIndicesMatchOpenMyau() {
        assertEquals(0, NoSlowModule.MODE_NONE);
        assertEquals(1, NoSlowModule.MODE_VANILLA);
        assertEquals(2, NoSlowModule.MODE_GRIM);
    }
}
```

- [ ] **Step 2: Run — FAIL**

```bash
./gradlew test --tests gnu.client.module.modules.player.NoSlowModeTest
```

- [ ] **Step 3: Implement `NoSlowModule`**

Mirror OpenMyau settings with GNUClient `ModeSetting` / `SliderSetting` / `BoolSetting`:

| Setting | Default |
|---------|---------|
| Sword mode | VANILLA (1) — modes NONE, VANILLA, GRIM |
| Sword motion | 100 (show when VANILLA) |
| Sword sprint | true |
| KillAura only | false |
| Food mode | NONE |
| Food motion / sprint | 100 / true |
| Bow mode | NONE |
| Bow motion / sprint | 100 / true |

Public API:

- `instance()` via ModuleManager `"NoSlow"`
- `isSwordActive()`, `isFoodActive()`, `isBowActive()`, `isAnyActive()`, `canSprint()`, `getMotionMultiplier()`
- On tick / living path: if `isEnabled() && isAnyActive()` → `FloatManager.INSTANCE.setFloatState(true, FloatModules.NO_SLOW)` else `false`
- `onDisable`: force float state false

Item checks (inline helpers on module or small package-private util):

- Sword: `Mc.isHoldingSword()` + using item (or KA-only gate like OpenMyau)
- Food: held item is food and `player.isUsingItem()`
- Bow: held item is bow and using

`isAnyActive()`: `player.isUsingItem() && (sword|food|bow active)` — same as OpenMyau.

Do **not** activate `BlinkModules.NO_SLOW` in this task.

- [ ] **Step 4: Register module; tests PASS; commit**

```bash
git add src/main/java/gnu/client/module/modules/player/NoSlowModule.java \
  src/test/java/gnu/client/module/modules/player/NoSlowModeTest.java \
  src/main/java/gnu/client/GnuClientMod.java
git commit -m "Add NoSlow module with OpenMyau settings and FloatManager wiring."
```

---

### Task 5: NoSlow mixin redirect

**Files:**
- Create: `src/main/java/gnu/client/mixin/impl/entity/MixinEntityPlayerSPNoSlow.java`
- Modify: `src/main/resources/mixins.gnuclient.json`

- [ ] **Step 1: Add mixin**

```java
package gnu.client.mixin.impl.entity;

import gnu.client.module.modules.player.NoSlowModule;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSPNoSlow {

    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z"
            )
    )
    private boolean gnuNoSlowIsUsing(EntityPlayerSP self) {
        NoSlowModule noSlow = NoSlowModule.instance();
        if (noSlow != null && noSlow.isEnabled() && noSlow.isAnyActive())
            return false;
        return self.isUsingItem();
    }
}
```

Add `"entity.MixinEntityPlayerSPNoSlow"` to `mixins.gnuclient.json`.

- [ ] **Step 2: `./gradlew build` — SUCCESS**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gnu/client/mixin/impl/entity/MixinEntityPlayerSPNoSlow.java \
  src/main/resources/mixins.gnuclient.json
git commit -m "Skip use-item slowdown via EntityPlayerSP NoSlow mixin."
```

---

### Task 6: Mc helpers for block start/stop (if missing)

**Files:**
- Modify: `src/main/java/gnu/client/runtime/mc/Mc.java`

- [ ] **Step 1: Ensure helpers exist for OpenMyau startBlock/stopBlock**

OpenMyau:

```java
PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
// stop:
C07 RELEASE_USE_ITEM + stopUsingItem()
```

Add if absent:

```java
public static void sendUseItemBlockPlacement(ItemStack stack) {
    if (stack == null) return;
    PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
}

public static void startSwordBlock(EntityPlayer player, ItemStack stack) {
    if (player == null || stack == null) return;
    sendUseItemBlockPlacement(stack);
    player.setItemInUse(stack, stack.getMaxItemUseDuration());
}

public static void stopSwordBlock(EntityPlayer player) {
    sendReleaseUseItem(player);
    if (player != null)
        player.stopUsingItem();
}
```

(`sendReleaseUseItem` already exists.) Prefer these over `sendUseItem()` controller path for packet parity with OpenMyau.

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileJava
git add src/main/java/gnu/client/runtime/mc/Mc.java
git commit -m "Add Mc sword-block placement helpers for KillAura autoblock."
```

---

### Task 7: `KillAuraAutoBlock` helper (core mode port)

**Files:**
- Create: `src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java`

- [ ] **Step 1: Port mode constants + state machine from OpenMyau**

```java
package gnu.client.module.modules.combat.killaura;

public final class KillAuraAutoBlock {
    public static final int NONE = 0;
    public static final int VANILLA = 1;
    public static final int SPOOF = 2;
    public static final int HYPIXEL = 3;
    public static final int BLINK = 4;
    public static final int INTERACT = 5;
    public static final int SWAP = 6;
    public static final int LEGIT = 7;
    public static final int FAKE = 8;

    // state owned by helper, driven by KillAuraModule each preUpdate:
    // isBlocking, fakeBlockState, blockingState, blockTick, blinkReset, attackAllowedOut
}
```

Implement methods (signatures approximate — match KA fields you wire):

- `void reset()` — clear blink AUTO_BLOCK, ticks, flags
- `boolean isPlayerBlocking()` — `(using || blockingState) && sword`
- `boolean canAutoBlock(requirePress)` — sword + optional use-key
- `boolean hasValidTargetInBlockRange(...)` — any living candidate within AutoBlockRange (reuse KA distance helpers)
- `PreResult onPreUpdate(...)` — full switch from OpenMyau cases 0–8:
  - digging: `IAccessorPlayerControllerMP.getIsHittingBlock()`
  - placing: treat `ScaffoldModule` enabled as placing (no PlayerStateManager)
  - Hypixel case 3: if `NoSlowModule.instance() != null && isEnabled()` → random C09 flick then `Mc.stopSwordBlock`
  - Blink/Interact: set `blinkReset`; Hypixel blocked path may `setBlinkState(false/true, AUTO_BLOCK)` like OpenMyau `blocked`
  - FAKE: `fakeBlockState = hasValidTarget`; no real block unless manual use
- `void onPostUpdate()` — if `blinkReset`: release+reacquire AUTO_BLOCK (OpenMyau POST)
- `void startBlock` / `stopBlock` / `interactAttack` via Mc + C02 interact packets
- `findEmptySlot` / `findSwordSlot` copy from OpenMyau

`performAttack` gate: if `isPlayerBlocking() && mode != VANILLA` → skip attack (OpenMyau). Expose `shouldDeferAttack()` for KA.

When `swap` after attack: `interactAttack` else `sendUseItem`/`startBlock` (OpenMyau).

- [ ] **Step 2: Compile only (no full KA wire yet)**

```bash
./gradlew compileJava
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/gnu/client/module/modules/combat/killaura/KillAuraAutoBlock.java
git commit -m "Port OpenMyau KillAura autoblock mode logic into KillAuraAutoBlock."
```

---

### Task 8: Wire settings + hooks into `KillAuraModule`

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/combat/KillAuraModule.java`

- [ ] **Step 1: Add settings (default NONE)**

```java
private static final List<String> AUTO_BLOCK_MODES = Arrays.asList(
    "NONE", "VANILLA", "SPOOF", "HYPIXEL", "BLINK", "INTERACT", "SWAP", "LEGIT", "FAKE");

private final ModeSetting autoBlock = addSetting(new ModeSetting("Auto-block", 0, AUTO_BLOCK_MODES));
private final SliderSetting autoBlockCps = addSetting(new SliderSetting("AutoBlockCPS", 8.0f, 1.0f, 10.0f));
private final SliderSetting autoBlockRange = addSetting(new SliderSetting("AutoBlockRange", 6.0f, 3.0f, 8.0f));
private final BoolSetting autoBlockRequirePress = addSetting(new BoolSetting("AutoBlockRequirePress", false));

private final KillAuraAutoBlock autoBlockHelper = new KillAuraAutoBlock();
```

- [ ] **Step 2: Integrate into `preUpdate`**

Order (OpenMyau PRE):

1. Update target (existing)
2. Run `autoBlockHelper.onPreCombat(...)` with mode/CPS/range/press, current target, attack delay state
3. If helper says defer attack / `attack=false`, skip `tryPerformAttack`
4. Else attack as today
5. Apply helper post-attack `swap` → startBlock / interactAttack
6. Apply Hypixel `blocked` blink pulse if helper requests

Also:

- When `isBlocking` from helper, use AutoBlockCPS for attack delay: `(long)(1000f / autoBlockCps.getValue())` (OpenMyau `getAttackDelay`)
- `onDisable` / no target / `!canRunCombat`: `autoBlockHelper.reset()` + `BlinkManager.INSTANCE.setBlinkState(false, AUTO_BLOCK)`
- Hook POST blinkReset: call from `PlayerUpdateHook.onUpdateReturn` path or `KillAuraModule.onAfterWalking` / static `onPostUpdate` — mirror OpenMyau POST. Prefer a static `KillAuraModule.onPostUpdate()` invoked from existing `PlayerUpdateHook.onUpdateReturn` if that already fans out modules; otherwise call at end of `afterWalking` only if that is post-living — **verify** against `PlayerUpdateHook` and use the earliest post-update hook that matches OpenMyau POST (after motion). If needed, subscribe helper to `PostUpdateEvent` from KA enable.

- [ ] **Step 3: Keep Grim guards**

Do not remove `AuraCombatPacketGuard` RELEASE-skip or sword world-C08 cancel.

- [ ] **Step 4: Build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gnu/client/module/modules/combat/KillAuraModule.java
git commit -m "Wire OpenMyau autoblock modes into KillAura (default NONE)."
```

---

### Task 9: Vault decision notes

**Files:**
- Create/update under `gnu client/` (Obsidian vault):  
  - `Decision - KillAura OpenMyau autoblock helper.md`  
  - `Decision - NoSlow FloatManager BlinkModules.md`  
- Update `Architecture.md` / `TODOs.md` / link from `Home.md` as needed

- [ ] **Step 1: Write decisions** — problem, decision (B + wired float + shared blink), rationale, wiki-links to KillAura / NoSlow / packet guards

- [ ] **Step 2: Commit vault notes if vault is in this git repo; otherwise leave for user vault sync**

(If `gnu client/` is outside `gnuclient recode` git, do not force-add; note path in PR/summary.)

---

### Task 10: Verification checklist (manual + build)

- [ ] **Step 1: Automated**

```bash
cd "gnuclient recode" && ./gradlew test build
```

Expected: all new tests PASS; jar in `build/libs/`

- [ ] **Step 2: Manual in-game (after stage jar)**

| Case | Expected |
|------|----------|
| Auto-block NONE | No KA block packets |
| VANILLA | Blocks with C08; can attack while blocking |
| HYPIXEL + NoSlow off | stopBlock without C09 flick |
| HYPIXEL + NoSlow on | C09 flick then RELEASE |
| BLINK | Packets held under AUTO_BLOCK; flush on release |
| NoSlow on + sword block | No 0.2× slow |
| KA disable | Blink AUTO_BLOCK cleared |

- [ ] **Step 3: Final commit only if leftover docs/fixes remain**

---

## Spec coverage (self-review)

| Spec item | Task |
|-----------|------|
| KA settings + default NONE | 8 |
| KillAuraAutoBlock helper + all 9 modes | 7–8 |
| Hypixel ↔ NoSlow C09 | 7 |
| BlinkManager AUTO_BLOCK | 1–2, 7–8 |
| BlinkModules.NO_SLOW reserved | 1, 4 (no activate) |
| NoSlow module + mixin | 4–5 |
| FloatManager wired from NoSlow | 3–4 |
| Grim guards KEEP | 8 |
| Vault | 9 |
| Build / verify | 10 |

**Placeholders:** none intentional — OpenMyau switch bodies are copied in Task 7 from reference file, not left as “TBD”.

**Type consistency:** `BlinkModules.AUTO_BLOCK` / `FloatModules.NO_SLOW` / mode ints 0–8 used throughout.
