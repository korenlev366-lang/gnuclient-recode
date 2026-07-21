# Scaffold Recode v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a thin new Scaffold module with KA-style silent rotations + Silent MoveFix, item spoof, four aim modes, KeepY Off/Telly, and vanilla jump tower.

**Architecture:** Mirror KillAura’s silent path (`PlayerUpdateHook.requestRotation` + `RotationState` priority 3 + `MoveFixUtil.fixStrafe`). Keep helpers small (`ScaffoldBlocks`, `ScaffoldPlace`, `ScaffoldAim`, `ScaffoldRotations`) — do **not** restore the deleted LiquidBounce technique tree. Spec: `docs/superpowers/specs/2026-07-22-scaffold-recode-design.md`.

**Tech Stack:** Java 8, Forge 1.8.9, Mixins, JUnit (pure math only), Gradle (`./gradlew build` / `test`).

---

## File structure

| Path | Responsibility |
|------|----------------|
| `src/main/java/gnu/client/module/modules/player/scaffold/ScaffoldModule.java` | Settings, enable/disable, tick orchestration, place, hooks |
| `.../scaffold/ScaffoldBlocks.java` | Valid block + biggest-stack hotbar pick |
| `.../scaffold/ScaffoldPlace.java` | Replaceable/support, face hit ray, rotations to point |
| `.../scaffold/ScaffoldAim.java` | Backwards / GodBridge / Nearest / Sideways target yaw/pitch |
| `.../scaffold/ScaffoldRotations.java` | Per-tick random speed step + GCD (KA `smoothRotationHv` family; package-local copy) |
| `.../scaffold/ScaffoldTarget.java` | Immutable place target (support pos, face, hit hint) |
| `src/main/java/gnu/client/runtime/ScaffoldItemSpoofHook.java` | Hotbar render spoof redirect |
| `src/main/java/gnu/client/mixin/impl/render/MixinGuiIngame.java` | Redirect `getCurrentItem` in `GuiIngame.updateTick` |
| `src/test/java/.../scaffold/ScaffoldBlocksTest.java` | Biggest-stack / validity |
| `src/test/java/.../scaffold/ScaffoldAimTest.java` | Aim mode yaw sanity (pure floats) |
| Modify: `GnuClientMod.java`, `PlayerUpdateHook.java`, `MovementInputHook.java`, `mixins.gnuclient.json` | Register + re-wire |

**Prerequisite working tree:** Scaffold deletion is already unstaged. Task 0 commits it before new files land.

---

### Task 0: Commit Scaffold removal

**Files:** all current deletions + unwires already in the working tree (no new edits).

- [ ] **Step 1: Confirm build still passes on removal**

```bash
cd "/home/lev/linux minecraft thing/gnuclient recode" && ./gradlew build -q
```

Expected: BUILD SUCCESSFUL (warnings OK).

- [ ] **Step 2: Commit removal only**

```bash
cd "/home/lev/linux minecraft thing/gnuclient recode"
git add -u src/main/java/gnu/client src/main/resources/mixins.gnuclient.json src/test
git status
git commit -m "$(cat <<'EOF'
chore: remove legacy Scaffold for recode

EOF
)"
```

Do not add `.qwen/`, `.superpowers/`, or unrelated untracked docs.

---

### Task 1: ScaffoldBlocks (biggest stack)

**Files:**
- Create: `src/main/java/gnu/client/module/modules/player/scaffold/ScaffoldBlocks.java`
- Test: `src/test/java/gnu/client/module/modules/player/scaffold/ScaffoldBlocksTest.java`

- [ ] **Step 1: Write failing test**

```java
package gnu.client.module.modules.player.scaffold;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScaffoldBlocksTest {
    @Test
    public void pickBestSlot_prefersLargerStack() {
        // Pure logic via package-visible helper that takes counts[] and valid[] flags
        int[] counts = {0, 16, 64, 32};
        boolean[] valid = {false, true, true, true};
        assertEquals(2, ScaffoldBlocks.pickBestSlot(counts, valid));
    }

    @Test
    public void pickBestSlot_tieBreaksLowestIndex() {
        int[] counts = {32, 32, 16};
        boolean[] valid = {true, true, true};
        assertEquals(0, ScaffoldBlocks.pickBestSlot(counts, valid));
    }

    @Test
    public void pickBestSlot_noneValidReturnsNeg1() {
        assertEquals(-1, ScaffoldBlocks.pickBestSlot(new int[]{1}, new boolean[]{false}));
    }
}
```


- [ ] **Step 2: Run test — expect fail**

```bash
./gradlew test --tests gnu.client.module.modules.player.scaffold.ScaffoldBlocksTest
```

Expected: compile fail or FAIL (class missing).

- [ ] **Step 3: Implement `ScaffoldBlocks`**

```java
package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.mc.Mc;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.BlockWeb;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public final class ScaffoldBlocks {
    private ScaffoldBlocks() {}

    public static boolean isValidBlock(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock) || stack.stackSize <= 0)
            return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        if (block == null || block instanceof BlockFalling)
            return false;
        if (block == Blocks.tnt || block instanceof BlockTNT)
            return false;
        if (block == Blocks.web || block instanceof BlockWeb)
            return false;
        if (block == Blocks.portal)
            return false;
        return true;
    }

    /** Testable: first max count among valid; ties → lowest index; none → -1. */
    public static int pickBestSlot(int[] counts, boolean[] valid) {
        int best = -1;
        int bestCount = -1;
        int n = Math.min(counts.length, valid.length);
        for (int i = 0; i < n; i++) {
            if (!valid[i])
                continue;
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                best = i;
            }
        }
        return best;
    }

    public static int pickBestHotbarSlot(EntityPlayerSP player) {
        if (player == null)
            return -1;
        int[] counts = new int[9];
        boolean[] valid = new boolean[9];
        for (int i = 0; i < 9; i++) {
            ItemStack s = Mc.getStackInSlot(player.inventory, i);
            valid[i] = isValidBlock(s);
            counts[i] = valid[i] ? s.stackSize : 0;
        }
        return pickBestSlot(counts, valid);
    }
}
```

- [ ] **Step 4: Run test — expect pass**

```bash
./gradlew test --tests gnu.client.module.modules.player.scaffold.ScaffoldBlocksTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gnu/client/module/modules/player/scaffold/ScaffoldBlocks.java \
  src/test/java/gnu/client/module/modules/player/scaffold/ScaffoldBlocksTest.java
git commit -m "feat(scaffold): biggest-stack hotbar picker"
```

---

### Task 2: ScaffoldPlace + ScaffoldRotations (GCD step)

**Files:**
- Create: `ScaffoldPlace.java`, `ScaffoldRotations.java`, `ScaffoldTarget.java`
- Test: `ScaffoldRotationsTest.java`

- [ ] **Step 1: Failing test for speed clamp + step moves toward target**

```java
package gnu.client.module.modules.player.scaffold;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScaffoldRotationsTest {
    @Test
    public void clampSpeedRange_swapsWhenMinGreaterThanMax() {
        int[] r = ScaffoldRotations.clampRange(80, 60);
        assertEquals(60, r[0]);
        assertEquals(80, r[1]);
    }

    @Test
    public void stepToward_withFullSpeedReachesTargetWhenGcdDisabled() {
        // When gcd<=0 path: speed 100 should land on target
        float[] out = ScaffoldRotations.stepTowardNoGcd(0f, 0f, 90f, 45f, 100);
        assertEquals(90f, out[0], 0.01f);
        assertEquals(45f, out[1], 0.01f);
    }
}
```

- [ ] **Step 2: Run — expect fail**

```bash
./gradlew test --tests gnu.client.module.modules.player.scaffold.ScaffoldRotationsTest
```

- [ ] **Step 3: Implement rotations + place math**

`ScaffoldRotations.java` — copy KA percent-step idea (`speed/100` of remaining delta) + GCD via `Mc.getMouseSensitivityGcd()`:

```java
package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.mc.Mc;
import java.util.concurrent.ThreadLocalRandom;

public final class ScaffoldRotations {
    private ScaffoldRotations() {}

    public static int[] clampRange(int min, int max) {
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        min = Math.max(1, Math.min(100, min));
        max = Math.max(1, Math.min(100, max));
        return new int[] { min, max };
    }

    public static int sampleSpeed(int min, int max) {
        int[] r = clampRange(min, max);
        if (r[0] >= r[1])
            return r[0];
        return ThreadLocalRandom.current().nextInt(r[0], r[1] + 1);
    }

    /** Test helper: no GCD, percent step both axes with same speed. */
    public static float[] stepTowardNoGcd(float by, float bp, float ty, float tp, int speed) {
        float dy = wrap(ty - by);
        float dp = tp - bp;
        float t = Math.min(1f, Math.max(0f, speed / 100f));
        return new float[] { by + dy * t, clampPitch(bp + dp * t) };
    }

    public static float[] stepToward(float baseYaw, float basePitch,
            float targetYaw, float targetPitch, int speed) {
        float[] stepped = stepTowardNoGcd(baseYaw, basePitch, targetYaw, targetPitch, speed);
        return applyGcd(stepped[0], stepped[1], baseYaw, basePitch);
    }

    static float[] applyGcd(float targetYaw, float targetPitch, float yaw, float pitch) {
        targetYaw = yaw + wrap(targetYaw - yaw);
        float deltaYaw = targetYaw - yaw;
        float deltaPitch = targetPitch - pitch;
        double gcd = Mc.getMouseSensitivityGcd();
        if (gcd <= 0.0)
            return new float[] { targetYaw, clampPitch(targetPitch) };
        float qYaw = (float) (Math.round(deltaYaw / gcd) * gcd);
        float qPitch = (float) (Math.round(deltaPitch / gcd) * gcd);
        return new float[] { yaw + qYaw, clampPitch(pitch + qPitch) };
    }

    public static float wrap(float a) {
        a %= 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    public static float clampPitch(float p) {
        return Math.max(-90f, Math.min(90f, p));
    }
}
```

`ScaffoldTarget.java`:

```java
package gnu.client.module.modules.player.scaffold;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public final class ScaffoldTarget {
    public final BlockPos support;
    public final EnumFacing face;
    public final BlockPos placed;

    public ScaffoldTarget(BlockPos support, EnumFacing face) {
        this.support = support;
        this.face = face;
        this.placed = support.offset(face);
    }
}
```

`ScaffoldPlace.java` — minimal APIs (implement with Minecraft types; unit tests optional for pure wrap already covered):

- `isReplaceable(World, BlockPos)`
- `isValidSupport(World, BlockPos)`
- `findNeighborTarget(EntityPlayerSP, World, BlockPos under)` — scan 6 faces of neighbors around `under` for a solid support whose `offset(face)` equals the replaceable cell (standard scaffold under-feet search). Prefer `EnumFacing.UP` when tower (caller passes preferUp).
- `rotationsTo(Vec3 point, EntityPlayer player, float baseYaw, float basePitch)` → yaw/pitch
- `findPlacementHit(player, support, face, yaw, pitch)` → hitVec or null (ray must match face)
- `rayTrace(yaw, pitch, reach)` using player eye + look vec

Use `Mc.controller().getBlockReachDistance()` when available else `4.5f`. Face center hit vec for aim pitch when building target rotation.

Reference for face match: old `ScaffoldMath.findPlacementHit` (git history) — must match intended face to avoid Grim ghost places.

- [ ] **Step 4: Tests pass**

```bash
./gradlew test --tests gnu.client.module.modules.player.scaffold.ScaffoldRotationsTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gnu/client/module/modules/player/scaffold/ScaffoldPlace.java \
  src/main/java/gnu/client/module/modules/player/scaffold/ScaffoldRotations.java \
  src/main/java/gnu/client/module/modules/player/scaffold/ScaffoldTarget.java \
  src/test/java/gnu/client/module/modules/player/scaffold/ScaffoldRotationsTest.java
git commit -m "feat(scaffold): place math and GCD rotation step"
```

---

### Task 3: ScaffoldAim modes

**Files:**
- Create: `ScaffoldAim.java`
- Test: `ScaffoldAimTest.java`

Constants:

```java
public static final int AIM_BACKWARDS = 0;
public static final int AIM_GODBRIDGE = 1;
public static final int AIM_NEAREST = 2;
public static final int AIM_SIDEWAYS = 3;
```

- [ ] **Step 1: Failing tests**

```java
@Test
public void backwards_isOppositeMovementYaw() {
    float moveYaw = 0f; // south in MC is positive Z; use wrap helpers
    float yaw = ScaffoldAim.backwardsYaw(moveYaw);
    assertEquals(180f, ScaffoldRotations.wrap(yaw - moveYaw), 0.01f);
}

@Test
public void godbridge_isDiagonal45() {
    float yaw = ScaffoldAim.godBridgeYaw(0f, true); // +45
    assertEquals(45f, ScaffoldRotations.wrap(yaw), 0.01f);
}
```

- [ ] **Step 2: Implement**

```java
public final class ScaffoldAim {
    private ScaffoldAim() {}

    public static float backwardsYaw(float moveYaw) {
        return moveYaw + 180f;
    }

    /** diagonal: +45 if preferRight else -45 from moveYaw. */
    public static float godBridgeYaw(float moveYaw, boolean preferRight) {
        return moveYaw + (preferRight ? 45f : -45f);
    }

    /**
     * @param aimMode AIM_* constant
     * @param moveYaw movement-facing yaw (MoveFixUtil.movementFacingYaw or camera)
     * @param hit aim point on face
     * @param player for eye / nearest
     * @param support/face for sideways search
     */
    public static float[] compute(int aimMode, float moveYaw, float baseYaw, float basePitch,
            EntityPlayer player, ScaffoldTarget target, Vec3 hitPrefer) {
        // NEAREST: rotationsTo(nearest point on face AABB from eye)
        // BACKWARDS: yaw=backwardsYaw(moveYaw); pitch from rotationsTo(face center or hitPrefer)
        // GODBRIDGE: try ±45; pick the one whose ray still hits face if possible, else preferRight from strafe
        // SIDEWAYS: sample yaw offsets from moveYaw in steps (e.g. ±90..±180); keep those where
        //   findPlacementHit != null; pick max |wrap(yaw - moveYaw)|
        // Always return GCD-ready raw target (quantize in stepToward).
    }
}
```

Implement `nearestPointOnFace(BlockPos support, EnumFacing face, Vec3 eye)` by clamping eye coords to the face rectangle.

GodBridge preferRight: `Mc.isRightKeyHeld() && !Mc.isLeftKeyHeld()` else if left then false else default true.

- [ ] **Step 3: Tests pass + commit**

```bash
./gradlew test --tests gnu.client.module.modules.player.scaffold.ScaffoldAimTest
git add src/main/java/gnu/client/module/modules/player/scaffold/ScaffoldAim.java \
  src/test/java/gnu/client/module/modules/player/scaffold/ScaffoldAimTest.java
git commit -m "feat(scaffold): aim modes backwards godbridge nearest sideways"
```

---

### Task 4: Item spoof render hook

**Files:**
- Create: `src/main/java/gnu/client/runtime/ScaffoldItemSpoofHook.java`
- Create: `src/main/java/gnu/client/mixin/impl/render/MixinGuiIngame.java`
- Modify: `src/main/resources/mixins.gnuclient.json` — add `"render.MixinGuiIngame"` after `MixinEntityRenderer`

- [ ] **Step 1: Restore spoof hook** (visual slot from module)

```java
package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.player.InventoryPlayer;

public final class ScaffoldItemSpoofHook {
    private ScaffoldItemSpoofHook() {}

    public static Object redirectCurrentItem(Object inventoryPlayer) {
        if (!(inventoryPlayer instanceof InventoryPlayer))
            return null;
        InventoryPlayer inventory = (InventoryPlayer) inventoryPlayer;
        ScaffoldModule scaffold = active();
        if (scaffold != null) {
            int spoof = scaffold.getSpoofSlot();
            if (spoof >= 0 && spoof <= 8)
                return Mc.getStackInSlot(inventory, spoof);
        }
        return inventory.getCurrentItem();
    }

    private static ScaffoldModule active() {
        Module m = ModuleManager.instance().getModule("Scaffold");
        if (!(m instanceof ScaffoldModule) || !m.isEnabled())
            return null;
        return (ScaffoldModule) m;
    }
}
```

`MixinGuiIngame` — same redirect as before removal (see git history `MixinGuiIngame.java`).

- [ ] **Step 2: Stub `ScaffoldModule` minimal** so hook compiles — only name, `getSpoofSlot()`, settings empty placeholders — **or** implement Task 5 first in same session before compile. Prefer completing Task 5 module shell before compiling this task.

- [ ] **Step 3: Commit spoof + mixin after module shell exists** (combine with Task 5 commit if needed).

---

### Task 5: ScaffoldModule shell + registration + hooks

**Files:**
- Create: `ScaffoldModule.java` (settings + lifecycle + static hooks)
- Modify: `GnuClientMod.java` — `safeRegister(new ScaffoldModule());`
- Modify: `PlayerUpdateHook.java` — call `ScaffoldModule.onPreUpdate` / `onBeforeWalkingPlace`
- Modify: `MovementInputHook.java` — call `ScaffoldModule.patchMovementInput` **before** KillAura (Scaffold prio 3; KA remaps only when it owns prio 1 — order: Scaffold then KA is fine)

Settings:

```java
private static final int KEEPY_OFF = 0;
private static final int KEEPY_TELLY = 1;

private final ModeSetting aim = addSetting(new ModeSetting("Aim", ScaffoldAim.AIM_BACKWARDS,
    Arrays.asList("Backwards", "GodBridge", "Nearest", "Sideways")));
private final ModeSetting keepY = addSetting(new ModeSetting("KeepY", KEEPY_OFF,
    Arrays.asList("Off", "Telly")));
private final SliderSetting rotMin = addSetting(new SliderSetting("Rotation min", 60f, 1f, 100f, 1f));
private final SliderSetting rotMax = addSetting(new SliderSetting("Rotation max", 80f, 1f, 100f, 1f));
```

Check existing `SliderSetting` constructors in codebase and match (step may be optional).

State fields:

```java
private int spoofSlot = -1;
private float lastSentYaw = Float.MIN_VALUE;
private float lastSentPitch = Float.MIN_VALUE;
private boolean tellyLookForward;
private ScaffoldTarget liveTarget;
```

Lifecycle:

```java
@Override
public void onEnable() {
    EntityPlayerSP p = Mc.player();
    spoofSlot = p != null ? Mc.getHotbarSlot(p) : 0;
    lastSentYaw = lastSentPitch = Float.MIN_VALUE;
}

@Override
public void onDisable() {
    clearRotationIfOwned();
    restoreHotbarToSpoof();
    liveTarget = null;
}

public int getSpoofSlot() { return spoofSlot; }

public static void onPreUpdate(Object player) { /* resolve module; preUpdate() */ }
public static void onBeforeWalkingPlace(Object player) { /* place with silent look */ }
public static void patchMovementInput(Object movInput) { /* MoveFix + Telly jump */ }
```

`clearRotationIfOwned`: if `RotationState.getPriority() == MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY` then `RotationState.applyState(false, ...)` / existing clear API — match KillAura `clearRotationStateIfOwned`.

`restoreHotbarToSpoof`: if player hotbar != spoofSlot, `Mc.setHotbarSlot(player, spoofSlot)` (vanilla C09 only when changed).

- [ ] **Step 1: Implement shell + register + hooks** (place/tick can no-op temporarily)

- [ ] **Step 2: `./gradlew build`** — expect SUCCESS

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(scaffold): module shell, register, spoof mixin, hooks"
```

---

### Task 6: Silent rotation, MoveFix, target find, place

**Files:** `ScaffoldModule.java` (main logic), possibly `ScaffoldPlace.findNeighborTarget`

Per-tick `preUpdate(EntityPlayerSP player)`:

1. `placeSlot = ScaffoldBlocks.pickBestHotbarSlot(player)`; if `<0` → clear rot, return.
2. Determine mode:
   - If jump held (`player.movementInput.jump` or key) and under feet replaceable → **tower** target: support = block below feet cell, face = UP (if support solid).
   - Else if KeepY Telly and (`player.onGround` || `player.motionY >= 0`) and need bridge extension → `tellyLookForward = true`, no place target required.
   - Else → bridge target under feet / edge (`ScaffoldPlace.findNeighborTarget`).
3. If `tellyLookForward`: target yaw = `MoveFixUtil.movementFacingYaw()` (or add public helper — if missing, use `Mc.getYaw()` while MoveFix off; when armed use camera yaw for “forward”). Pitch ≈ `player.rotationPitch` clamped or `75` walk — use `Math.max(player.rotationPitch, 70f)` only if needed; prefer keep current pitch near 70–80 for bridge feel, for forward use `~0..30`. Spec: “pitch near neutral / walk pitch” → use `20f` or player pitch.
4. Else if target != null: `float[] raw = ScaffoldAim.compute(...)`.
5. Base = lastSent or `PlayerUpdateHook.lastReportedYaw/Pitch`.
6. `speed = ScaffoldRotations.sampleSpeed(round(min), round(max))`.
7. `float[] sent = ScaffoldRotations.stepToward(baseYaw, basePitch, raw[0], raw[1], speed)`.
8. `PlayerUpdateHook.requestRotation(sent[0], sent[1])`; save lastSent.
9. `RotationState.applyState(true, sent[0], sent[1], sent[0], MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY)`.

`onBeforeWalkingPlace`:

1. If tellyLookForward → return (no place).
2. If no liveTarget or placeSlot < 0 → return.
3. Switch hotbar to placeSlot **only if** `Mc.getHotbarSlot(player) != placeSlot` (skip C09 when same).
4. If `ScaffoldPlace.findPlacementHit(...) == null` → return (still rotating).
5. `Mc.controller().onPlayerRightClick(...)` + `swingItem()` as AutoPlace does.
6. Do **not** leave hotbar on place slot visually — after place, set hotbar back to `spoofSlot` if different (OpenMyau spoof: some clients keep place slot during gameplay and only spoof render. Spec: “gameplay set currentItem to place slot when placing; render shows visual”. Prefer: switch to place for the right-click packet, then immediately restore spoof slot if different so inventory state matches spoof; render hook still shows spoof).

`patchMovementInput`:

```java
if (!MoveFixUtil.hasMoveFixPriority(MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY))
    return;
if (!MoveFixUtil.isForwardPressed() && !tellyJump)
    ; // still allow jump inject
MovementInput in = (MovementInput) movInput;
if (MoveFixUtil.hasMoveFixPriority(...) && MoveFixUtil.isForwardPressed()) {
    float[] fixed = MoveFixUtil.fixStrafe(Mc.getYaw(), RotationState.getSmoothedYaw(), in.sneak);
    in.moveForward = fixed[0];
    in.moveStrafe = fixed[1];
}
if (shouldTellyJump())
    in.jump = true;
```

`shouldTellyJump`: KeepY Telly && enabled && has blocks && (onGround edge / under next is air) — simple: Telly && onGround && replaceable under next step OR replaceable under feet while moving forward.

- [ ] **Step 1: Implement findNeighborTarget + module tick/place**

- [ ] **Step 2: `./gradlew build`**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(scaffold): silent aim MoveFix place and spoof switch"
```

---

### Task 7: Telly + tower polish

**Files:** `ScaffoldModule.java`

- [ ] **Step 1: Telly state machine**

```text
if keepY != TELLY:
  tellyLookForward = false
else if player.onGround || motionY >= 0:
  if needsBridgeExtension: tellyLookForward = true; request jump via patchMovementInput
  else: tellyLookForward = false; normal place if needed
else: // airborne falling
  tellyLookForward = false; find place target; aim + place
```

Tower: if jump held (player wants tower) **override** tellyLookForward for that tick — prefer under-feet UP place even in Telly.

- [ ] **Step 2: Build**

```bash
./gradlew build
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(scaffold): KeepY Telly and vanilla jump tower"
```

---

### Task 8: Verification checklist

- [ ] `./gradlew test` and `./gradlew build` both green
- [ ] Manual (in-game): ClickGUI shows Aim / KeepY / Rotation min / max (defaults 60/80)
- [ ] F5 head follows silent look; strafing MoveFixed
- [ ] Hotbar visually stays on spoof slot; places from biggest stack; no C09 when already on that stack
- [ ] KeepY Off bridges; Telly jumps, looks forward rising, places when `motionY < 0`
- [ ] Hold space towers in Off and Telly
- [ ] Disable restores slot without redundant C09 when unchanged

No extra commit required if Task 7 already committed; otherwise fix commits as needed.

---

## Spec coverage (self-review)

| Spec item | Task |
|-----------|------|
| Silent rot + MoveFix prio 3 | 5–6 |
| GCD + F5 | 2, 6 |
| min/max random 60/80 | 2, 5–6 |
| Aim modes | 3, 6 |
| KeepY Off / Telly | 7 |
| Item spoof always | 4–6 |
| Skip C09 same slot | 6 |
| Vanilla tower | 6–7 |
| No technique tree / blink / sprint modes | honored |
| Removal committed | 0 |

**Placeholders:** none intentional. `ScaffoldPlace.findNeighborTarget` algorithm is specified as standard under-feet neighbor scan; implementer may use 4-horizontal + down search order (UP last except tower).

**Types:** `ScaffoldTarget`, `AIM_*`, `KEEPY_*`, `getSpoofSlot()`, `sampleSpeed`, `stepToward` — consistent across tasks.
