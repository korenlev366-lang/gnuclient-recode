# Entity Culling (Performance Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Skip vanilla entity rendering via frustum + Aggressive category/distance filters, controlled by Performance `Entity Cull` + Lite/Aggressive, without affecting combat/ESP.

**Architecture:** Pure decision helpers in `EntityCull` (category/distance unit-tested); per-frame frustum cache; `@Inject` HEAD on `RenderManager.doRenderEntity` cancels when `shouldRenderEntity` is false. Existing Reduced Entity Distance redirect stays. Combat/ESP keep iterating `loadedEntityList` unchanged.

**Tech Stack:** Forge 1.8.9, SpongeMixins, `net.minecraft.client.renderer.culling.Frustum`, JUnit for pure logic.

**Spec:** `docs/superpowers/specs/2026-07-20-entity-culling-design.md`

---

## File map

| File | Responsibility |
|------|----------------|
| `util/EntityCull.java` | `shouldRenderEntity`, category/distance helpers, frustum cache |
| `module/modules/settings/PerformanceModule.java` | `Entity Cull` + `Cull Mode` + accessors |
| `mixin/impl/render/MixinRenderManagerCull.java` | HEAD cancel inject (keep RD redirect) |
| `src/test/java/gnu/client/util/EntityCullTest.java` | Category + distance pure-logic tests |

---

### Task 1: Pure cull helpers + unit tests

**Files:**
- Create: `src/main/java/gnu/client/util/EntityCull.java`
- Create: `src/test/java/gnu/client/util/EntityCullTest.java`

- [x] **Step 1: Write failing tests**

```java
package gnu.client.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class EntityCullTest {

    @Test
    public void liteNeverSkipsByCategory() {
        assertFalse(EntityCull.skipByCategory(false, false, true, false, true));
        assertFalse(EntityCull.skipByCategory(false, false, false, true, false));
        assertFalse(EntityCull.skipByCategory(false, false, false, false, true));
    }

    @Test
    public void aggressiveSkipsItemsStandsNonPlayerLiving() {
        assertTrue(EntityCull.skipByCategory(true, false, true, false, false));  // item
        assertTrue(EntityCull.skipByCategory(true, false, false, true, false)); // armor stand
        assertTrue(EntityCull.skipByCategory(true, false, false, false, true)); // mob
        assertFalse(EntityCull.skipByCategory(true, true, false, false, true)); // player
    }

    @Test
    public void aggressiveDistanceUsesHardMax() {
        float max = EntityCull.AGGRESSIVE_MAX_DISTANCE;
        assertEquals(48f, max, 0.001f);
        assertFalse(EntityCull.skipByDistance(true, 40 * 40, max));
        assertTrue(EntityCull.skipByDistance(true, 50 * 50, max));
        assertFalse(EntityCull.skipByDistance(false, 999999, max)); // Lite: distance helper no-op
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (class missing)**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.util.EntityCullTest --no-daemon
```

Expected: FAIL compiling / class not found.

- [ ] **Step 3: Implement pure helpers (+ stub `shouldRenderEntity` for later)**

Create `src/main/java/gnu/client/util/EntityCull.java`:

```java
package gnu.client.util;

import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

/**
 * Render-only entity culling for {@code RenderManager.doRenderEntity}.
 * Does not affect ESP / combat entity iteration.
 */
public final class EntityCull {

    public static final float AGGRESSIVE_MAX_DISTANCE = 48f;

    private static final Frustum FRUSTUM = new Frustum();
    private static int cachedFrame = -1;

    private EntityCull() {}

    /** Aggressive category skip. Lite ({@code aggressive == false}) never skips. */
    public static boolean skipByCategory(
            boolean aggressive,
            boolean isPlayer,
            boolean isItem,
            boolean isArmorStand,
            boolean isNonPlayerLiving) {
        if (!aggressive)
            return false;
        if (isPlayer)
            return false;
        return isItem || isArmorStand || isNonPlayerLiving;
    }

    /** Aggressive hard distance. Lite never skips here (RD redirect handles soft distance). */
    public static boolean skipByDistance(boolean aggressive, double distSq, float maxDist) {
        if (!aggressive)
            return false;
        return distSq > (double) maxDist * (double) maxDist;
    }

    /**
     * @return true if vanilla should render this entity.
     * Stub until Task 3 wires frustum — for now: if cull off → true; else category+distance only.
     */
    public static boolean shouldRenderEntity(Entity entity) {
        if (!PerformanceModule.entityCull())
            return true;
        if (entity == null)
            return true;
        Entity self = Mc.player();
        if (self != null && (entity == self || entity == self.ridingEntity))
            return true;

        boolean aggressive = PerformanceModule.cullModeAggressive();
        boolean isPlayer = entity instanceof EntityPlayer;
        boolean isItem = entity instanceof EntityItem;
        boolean isArmorStand = entity instanceof EntityArmorStand;
        boolean isNonPlayerLiving = entity instanceof EntityLivingBase && !isPlayer;

        if (skipByCategory(aggressive, isPlayer, isItem, isArmorStand, isNonPlayerLiving))
            return false;

        Entity viewer = Mc.renderViewEntity();
        if (viewer == null)
            viewer = self;
        if (viewer != null && skipByDistance(aggressive, entity.getDistanceSqToEntity(viewer), AGGRESSIVE_MAX_DISTANCE))
            return false;

        // Frustum filled in Task 3
        return true;
    }
}
```

Note: `Mc.renderViewEntity()` — if missing, use existing pattern from `Mc.java` (`getRenderViewEntity` / similar). Prefer `Mc` helpers over raw `Minecraft.getMinecraft()`.

- [ ] **Step 4: Run tests — expect PASS**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.util.EntityCullTest --no-daemon
```

Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 5: Commit** (only if user asked to commit)

```bash
git add src/main/java/gnu/client/util/EntityCull.java src/test/java/gnu/client/util/EntityCullTest.java
git commit -m "$(cat <<'EOF'
feat: add EntityCull category/distance helpers

EOF
)"
```

---

### Task 2: Performance settings + accessors

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/settings/PerformanceModule.java`

- [ ] **Step 1: Add settings after the Entities block (near `reducedEntityDistance`)**

```java
import gnu.client.module.setting.ModeSetting;
// Arrays already imported

private final BoolSetting entityCull = addSetting(new BoolSetting("Entity Cull", false));
private final ModeSetting cullMode = addSetting(
        new ModeSetting("Cull Mode", 0, Arrays.asList("Lite", "Aggressive"))
                .visibleWhen(() -> entityCull.isToggled()));
```

- [ ] **Step 2: Add accessors**

```java
public static boolean entityCull() {
    return instance != null && instance.entityCull.isToggled();
}

/** true when Cull Mode is Aggressive (index 1). */
public static boolean cullModeAggressive() {
    return instance != null && instance.cullMode.getValue() == 1;
}

public static float aggressiveCullDistance() {
    return EntityCull.AGGRESSIVE_MAX_DISTANCE;
}
```

Import `gnu.client.util.EntityCull` only if using the constant in the accessor; otherwise hardcode `48f` in the accessor to avoid a circular feel — prefer importing `EntityCull.AGGRESSIVE_MAX_DISTANCE`.

- [ ] **Step 3: Compile**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew compileJava --no-daemon
```

Expected: SUCCESS.

- [ ] **Step 4: Commit** (only if user asked)

```bash
git add src/main/java/gnu/client/module/modules/settings/PerformanceModule.java
git commit -m "$(cat <<'EOF'
feat: Entity Cull + Cull Mode settings on Performance

EOF
)"
```

---

### Task 3: Per-frame frustum + full `shouldRenderEntity`

**Files:**
- Modify: `src/main/java/gnu/client/util/EntityCull.java`

- [ ] **Step 1: Change API to `shouldRenderEntity(Entity, float partialTicks)` and add frustum cache**

Replace the Task 1 stub body with:

```java
private static double lastVx, lastVy, lastVz;
private static float lastPartial = Float.NaN;

private static void ensureFrustum(Entity viewer, float partialTicks) {
    double vx = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
    double vy = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
    double vz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;
    if (partialTicks == lastPartial && vx == lastVx && vy == lastVy && vz == lastVz)
        return;
    lastPartial = partialTicks;
    lastVx = vx;
    lastVy = vy;
    lastVz = vz;
    // Vanilla EntityRenderer refreshes ClippingHelperImpl before entity rendering;
    // Frustum only needs the camera position for isBoundingBoxInFrustum.
    FRUSTUM.setPosition(vx, vy, vz);
}

public static boolean shouldRenderEntity(Entity entity, float partialTicks) {
    if (!PerformanceModule.entityCull())
        return true;
    if (entity == null)
        return true;
    Entity self = Mc.player();
    if (self != null && (entity == self || entity == self.ridingEntity))
        return true;

    boolean aggressive = PerformanceModule.cullModeAggressive();
    boolean isPlayer = entity instanceof EntityPlayer;
    boolean isItem = entity instanceof EntityItem;
    boolean isArmorStand = entity instanceof EntityArmorStand;
    boolean isNonPlayerLiving = entity instanceof EntityLivingBase && !isPlayer;

    if (skipByCategory(aggressive, isPlayer, isItem, isArmorStand, isNonPlayerLiving))
        return false;

    Entity viewer = Mc.renderViewEntity(); // or Mc equivalent; fall back to self
    if (viewer == null)
        viewer = self;
    if (viewer != null
            && skipByDistance(aggressive, entity.getDistanceSqToEntity(viewer), AGGRESSIVE_MAX_DISTANCE))
        return false;

    if (viewer != null) {
        ensureFrustum(viewer, partialTicks);
        if (!FRUSTUM.isBoundingBoxInFrustum(entity.getEntityBoundingBox()))
            return false;
    }
    return true;
}
```

Remove unused `cachedFrame` field from Task 1 stub. Confirm `Mc.renderViewEntity()` exists (or add a one-liner wrapper next to `player()`).

- [ ] **Step 2: Compile**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew compileJava --no-daemon
```

Expected: SUCCESS. Fix any Frustum / MCP name mismatches.

- [ ] **Step 3: Re-run EntityCullTest**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.util.EntityCullTest --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Commit** (only if user asked)

```bash
git add src/main/java/gnu/client/util/EntityCull.java
git commit -m "$(cat <<'EOF'
feat: EntityCull frustum cache for render-only culling

EOF
)"
```

---

### Task 4: Mixin HEAD cancel

**Files:**
- Modify: `src/main/java/gnu/client/mixin/impl/render/MixinRenderManagerCull.java`

- [ ] **Step 1: Add cancellable HEAD inject**

Keep the existing `gnu$scaledRenderDistance` redirect. Add:

```java
import gnu.client.util.EntityCull;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
// doRenderEntity returns boolean in 1.8.9 — use CallbackInfoReturnable<Boolean>

@Inject(method = "doRenderEntity", at = @At("HEAD"), cancellable = true)
private void gnu$entityCull(
        Entity entity, double x, double y, double z, float entityYaw, float partialTicks,
        boolean hideDebugBox, CallbackInfoReturnable<Boolean> cir) {
    if (!EntityCull.shouldRenderEntity(entity)) {
        cir.setReturnValue(false);
    }
}
```

**Signature check:** Open MCP / existing mappings for exact `doRenderEntity` descriptor. If `CallbackInfo` vs `CallbackInfoReturnable` differs, match return type. If method is `void`, use `CallbackInfo` + `ci.cancel()`.

Pass `partialTicks` into `EntityCull` if Task 3 needs it — either overload `shouldRenderEntity(Entity, float)` or set a thread-local/static `currentPartialTicks` in the inject before the call:

```java
EntityCull.setPartialTicks(partialTicks);
if (!EntityCull.shouldRenderEntity(entity))
    cir.setReturnValue(false);
```

Prefer `shouldRenderEntity(Entity entity, float partialTicks)` as the public API.

- [ ] **Step 2: Compile + remapJar**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew compileJava remapJar --no-daemon
```

Expected: SUCCESS. Jar at `build/libs/gnuclient-*.jar`.

- [ ] **Step 3: Commit** (only if user asked)

```bash
git add src/main/java/gnu/client/mixin/impl/render/MixinRenderManagerCull.java src/main/java/gnu/client/util/EntityCull.java
git commit -m "$(cat <<'EOF'
feat: cancel doRenderEntity when EntityCull skips

EOF
)"
```

---

### Task 5: Manual verification checklist

**Files:** none (in-game)

- [ ] **Step 1: Entity Cull off** — crowded lobby looks identical to pre-change.
- [ ] **Step 2: Lite** — turn camera away from mobs/players; off-screen entities stop rendering; turn back → they reappear.
- [ ] **Step 3: Aggressive** — dropped items / armor stands / animals vanish; players within 48 and in frustum still render.
- [ ] **Step 4: ESP on + Aggressive** — ESP boxes still appear for players behind you / beyond frustum.
- [ ] **Step 5: KillAura** — still locks targets that are culled from vanilla render.
- [ ] **Step 6: Mount / ride** — ridden entity always visible.

---

## Spec coverage

| Spec requirement | Task |
|------------------|------|
| Master Entity Cull + Lite/Aggressive | Task 2 |
| Frustum | Task 3 |
| Aggressive distance 48 | Task 1 / 3 |
| Skip item / armor stand / non-player living | Task 1 / 3 |
| Never skip self / ridden | Task 1 stub / 3 |
| Players not category-skipped | Task 1 tests |
| Render-only (ESP/combat untouched) | no code changes to Esp/KA — Task 5 verifies |
| Mixin doRenderEntity | Task 4 |
| Keep Reduced Entity Distance redirect | Task 4 |

## Out of scope (do not implement in this plan)

- Phase 2 allocations, Phase 3 threading, occlusion culling.
