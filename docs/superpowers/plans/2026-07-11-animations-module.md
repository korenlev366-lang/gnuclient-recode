# Animations Module (OpenMyau parity) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Visuals `Animations` module with OpenMyau’s 16 block styles, Scale, and SwingSpeed on the GNUClient Forge recode path.

**Architecture:** GNUClient-native `AnimationsModule` owns settings; mixins read `AnimationsModule.instance()` directly (no `AnimationConfig`). ItemRenderer mixin redirects the block-path `transformFirstPersonItem` and injects OpenMyau GL transforms + scale; EntityLivingBase mixin injects swing duration. When the module is **disabled**, the redirect calls the original transform (fix OpenMyau’s always-suppress bug).

**Tech Stack:** Java 8, Forge 1.8.9, Sponge Mixin, existing `Module` / `ModeSetting` / `SliderSetting`, JUnit 4 tests under `src/test/java`.

**Spec:** `docs/superpowers/specs/2026-07-11-animations-module-design.md`  
**Reference:** `OpenMyau-Plus/src/main/java/myau/mixin/MixinItemRendererAnimations.java`

---

## File map

| Path | Responsibility |
|------|----------------|
| `src/main/java/gnu/client/module/modules/visual/AnimationsModule.java` | Module, settings, `instance()`, swing helper, suffix |
| `src/main/java/gnu/client/mixin/impl/accessors/IAccessorItemRenderer.java` | `equippedProgress` / `prevEquippedProgress` |
| `src/main/java/gnu/client/mixin/impl/render/MixinItemRendererAnimations.java` | Block transforms + scale |
| `src/main/java/gnu/client/mixin/impl/entity/MixinEntityLivingBaseAnimations.java` | Swing speed inject |
| `src/main/java/gnu/client/GnuClientMod.java` | `safeRegister(new AnimationsModule())` |
| `src/main/resources/mixins.gnuclient.json` | Register three mixin entries |
| `src/test/java/gnu/client/module/modules/visual/AnimationsSwingTest.java` | Pure swing-length formula tests |

---

### Task 1: Swing helper + unit tests

**Files:**
- Create: `src/main/java/gnu/client/module/modules/visual/AnimationsModule.java` (stub with helper only first, or full module in Task 2 — prefer full module in Task 2; this task only adds a package-visible helper class **or** static method on the module file)
- Create: `src/test/java/gnu/client/module/modules/visual/AnimationsSwingTest.java`

- [ ] **Step 1: Write the failing test**

```java
package gnu.client.module.modules.visual;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AnimationsSwingTest {
    @Test
    public void swingSpeedZeroIsVanillaSix() {
        assertEquals(6, AnimationsModule.armSwingAnimationEnd(0));
    }

    @Test
    public void swingSpeedHundredIsTwenty() {
        assertEquals(20, AnimationsModule.armSwingAnimationEnd(100));
    }

    @Test
    public void swingSpeedClampsBelowZero() {
        assertEquals(6, AnimationsModule.armSwingAnimationEnd(-10));
    }

    @Test
    public void swingSpeedClampsAboveHundred() {
        assertEquals(20, AnimationsModule.armSwingAnimationEnd(200));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd "gnuclient recode" && ./gradlew test --tests gnu.client.module.modules.visual.AnimationsSwingTest`

Expected: FAIL (class or method missing)

- [ ] **Step 3: Add static helper on `AnimationsModule` (minimal class OK)**

Create `AnimationsModule.java` with at least:

```java
package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;

public final class AnimationsModule extends Module {
    public AnimationsModule() {
        super("Animations", "Custom first-person swing and block animations", Category.VISUALS);
    }

    @Override public void onEnable() {}
    @Override public void onDisable() {}

    /** OpenMyau / syuto formula: 6 + pct/100*14, pct clamped 0..100. */
    public static int armSwingAnimationEnd(int swingSpeedPct) {
        int pct = Math.max(0, Math.min(100, swingSpeedPct));
        return (int) (6.0D + (double) pct / 100.0D * 14.0D);
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew test --tests gnu.client.module.modules.visual.AnimationsSwingTest`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/gnu/client/module/modules/visual/AnimationsModule.java \
  src/test/java/gnu/client/module/modules/visual/AnimationsSwingTest.java
git commit -m "Add Animations swing-speed helper and unit tests."
```

---

### Task 2: Complete `AnimationsModule` settings + registration

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/visual/AnimationsModule.java`
- Modify: `src/main/java/gnu/client/GnuClientMod.java` (add import + `safeRegister(new AnimationsModule());` near other Visuals modules)

- [ ] **Step 1: Expand module with Mode / Scale / SwingSpeed and instance API**

Mode list order **must** match OpenMyau indices 0..15:

```java
private static final java.util.List<String> MODES = java.util.Arrays.asList(
    "Vanilla", "Exhibition", "ETB", "Sigma", "Dortware", "Plain",
    "Spin", "Avatar", "Swong", "Swang", "Swank", "Styles",
    "Nudge", "Punch", "Jigsaw", "Slide");

public static final int MODE_VANILLA = 0;
public static final int MODE_EXHIBITION = 1;
// ... through MODE_SLIDE = 15 (define all constants used by the mixin)

private final ModeSetting mode = addSetting(new ModeSetting("Mode", MODE_VANILLA, MODES));
private final SliderSetting scale = addSetting(new SliderSetting("Scale", 100f, 50f, 150f, 1f));
private final SliderSetting swingSpeed = addSetting(new SliderSetting("SwingSpeed", 0f, 0f, 100f, 1f));

private static AnimationsModule INSTANCE;

public AnimationsModule() {
    super("Animations", "Custom first-person swing and block animations", Category.VISUALS);
    INSTANCE = this;
    setEnabled(true); // default on (OpenMyau); config load may override later
}

public static AnimationsModule instance() {
    return INSTANCE;
}

public int getModeIndex() { return mode.getValue(); }
public int getScalePct() { return Math.round(scale.getValue()); }
public int getSwingSpeedPct() { return Math.round(swingSpeed.getValue()); }

@Override
public String[] getSuffix() {
    return new String[] { mode.getCurrentMode() };
}
```

Keep `armSwingAnimationEnd` from Task 1.

- [ ] **Step 2: Register in `GnuClientMod.registerModules`**

After `safeRegister(new HudModule());` (or with other visuals):

```java
safeRegister(new AnimationsModule());
```

Add import: `gnu.client.module.modules.visual.AnimationsModule`

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gnu/client/module/modules/visual/AnimationsModule.java \
  src/main/java/gnu/client/GnuClientMod.java
git commit -m "Register Animations module with Mode, Scale, and SwingSpeed."
```

---

### Task 3: ItemRenderer accessor

**Files:**
- Create: `src/main/java/gnu/client/mixin/impl/accessors/IAccessorItemRenderer.java`
- Modify: `src/main/resources/mixins.gnuclient.json`

- [ ] **Step 1: Create accessor**

```java
package gnu.client.mixin.impl.accessors;

import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderer.class)
public interface IAccessorItemRenderer {
    @Accessor("equippedProgress")
    float getEquippedProgress();

    @Accessor("prevEquippedProgress")
    float getPrevEquippedProgress();
}
```

- [ ] **Step 2: Add to `mixins.gnuclient.json` mixins array**

```json
"accessors.IAccessorItemRenderer",
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gnu/client/mixin/impl/accessors/IAccessorItemRenderer.java \
  src/main/resources/mixins.gnuclient.json
git commit -m "Add ItemRenderer equipped-progress accessor for Animations."
```

---

### Task 4: `MixinItemRendererAnimations`

**Files:**
- Create: `src/main/java/gnu/client/mixin/impl/render/MixinItemRendererAnimations.java`
- Modify: `src/main/resources/mixins.gnuclient.json`

- [ ] **Step 1: Implement mixin**

Port transforms from OpenMyau `MixinItemRendererAnimations` with these GNUClient differences:

1. Gate on `AnimationsModule.instance()` + `isEnabled()`.
2. Mode via `getModeIndex()` compared to `AnimationsModule.MODE_*` constants (if-else, **not** switch).
3. **Redirect:** when module disabled/null, call `transformFirstPersonItem(f1, f2)`; when enabled, no-op (inject supplies transforms).
4. Scale from `getScalePct() / 100.0`.
5. Use `IAccessorItemRenderer` for equip progress.
6. `@Mixin(value = ItemRenderer.class, priority = 999)`.

Skeleton (fill every mode branch with OpenMyau’s exact GL calls — copy from reference file lines 67–154):

```java
package gnu.client.mixin.impl.render;

import gnu.client.mixin.impl.accessors.IAccessorItemRenderer;
import gnu.client.module.modules.visual.AnimationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = ItemRenderer.class, priority = 999)
public abstract class MixinItemRendererAnimations {

    private float spin;

    @Shadow @Final private Minecraft mc;

    @Shadow
    protected abstract void transformFirstPersonItem(float equipProgress, float swingProgress);

    @Redirect(
        method = "renderItemInFirstPerson",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;transformFirstPersonItem(FF)V",
            ordinal = 2))
    private void redirectBlockTransform(ItemRenderer instance, float equip, float swing) {
        AnimationsModule mod = AnimationsModule.instance();
        if (mod != null && mod.isEnabled()) {
            return;
        }
        transformFirstPersonItem(equip, swing);
    }

    @Inject(
        method = "renderItemInFirstPerson",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;doBlockTransformations()V"))
    private void applyAnimTransform(float partialTicks, CallbackInfo ci) {
        AnimationsModule mod = AnimationsModule.instance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        IAccessorItemRenderer acc = (IAccessorItemRenderer) this;
        float equipped = acc.getEquippedProgress();
        float prev = acc.getPrevEquippedProgress();
        float f = 1.0F - (prev + (equipped - prev) * partialTicks);
        AbstractClientPlayer player = this.mc.thePlayer;
        float swingProgress = player.getSwingProgress(partialTicks);
        float sine = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * 3.1415927F);
        float sqrtSwing = MathHelper.sqrt_float(swingProgress);
        float sine1 = MathHelper.sin(swingProgress * swingProgress * 3.1415927F);
        int m = mod.getModeIndex();
        // if-else for each MODE_* — copy GL body from OpenMyau (Exhibition, Sigma, … Jigsaw)
        // Example Exhibition:
        if (m == AnimationsModule.MODE_EXHIBITION) {
            GL11.glTranslated(0.0D, -0.1D, 0.0D);
            transformFirstPersonItem(f / 2.0F, 0.0F);
            GL11.glTranslatef(0.1F, 0.4F, -0.1F);
            GL11.glRotated(-sine * 30.0F, sine / 2.0F, 0.0D, 9.0D);
            GL11.glRotated(-sine * 50.0F, 0.8D, sine / 2.0F, 0.0D);
        } else if (m == AnimationsModule.MODE_SIGMA) {
            // ... full OpenMyau body for every mode ...
        }
        // MUST include all 16 modes; do not leave stubs.
    }

    @Inject(
        method = "renderItemInFirstPerson",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
            shift = At.Shift.BEFORE))
    private void applyScale(float partialTicks, CallbackInfo ci) {
        AnimationsModule mod = AnimationsModule.instance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        double s = (double) mod.getScalePct() / 100.0D;
        GL11.glScaled(s, s, s);
    }
}
```

**Implementer note:** When writing the file, paste the complete if-else chain from OpenMyau (all modes). The abbreviated `// ...` above is only for plan length — the committed Java file must contain every branch.

- [ ] **Step 2: Register mixin**

```json
"render.MixinItemRendererAnimations",
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`

Expected: SUCCESS (mixin AP may warn on descriptors — OK if build passes)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gnu/client/mixin/impl/render/MixinItemRendererAnimations.java \
  src/main/resources/mixins.gnuclient.json
git commit -m "Add ItemRenderer Animations mixin with OpenMyau block styles."
```

---

### Task 5: Swing speed mixin

**Files:**
- Create: `src/main/java/gnu/client/mixin/impl/entity/MixinEntityLivingBaseAnimations.java`
- Modify: `src/main/resources/mixins.gnuclient.json`

- [ ] **Step 1: Create inject mixin**

```java
package gnu.client.mixin.impl.entity;

import gnu.client.module.modules.visual.AnimationsModule;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = EntityLivingBase.class, priority = 999)
public abstract class MixinEntityLivingBaseAnimations {

    @Inject(method = { "getArmSwingAnimationEnd", "func_82166_i" }, at = @At("HEAD"), cancellable = true)
    private void gnuAnimationsSwingSpeed(CallbackInfoReturnable<Integer> cir) {
        AnimationsModule mod = AnimationsModule.instance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        cir.setReturnValue(AnimationsModule.armSwingAnimationEnd(mod.getSwingSpeedPct()));
    }
}
```

If `func_82166_i` is wrong SRG for 1.8.9, verify with:

```bash
javap -classpath ~/.gradle/caches/essential-loom/1.8.9/.../minecraft-mapped.jar -c net.minecraft.entity.EntityLivingBase | rg -n "ArmSwing|func_82166"
```

Use the MCP + SRG names that exist (common alternate: check yarn/MCP for `getArmSwingAnimationEnd`).

- [ ] **Step 2: Register**

```json
"entity.MixinEntityLivingBaseAnimations",
```

- [ ] **Step 3: Compile + unit tests**

Run: `./gradlew test --tests gnu.client.module.modules.visual.AnimationsSwingTest compileJava`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/gnu/client/mixin/impl/entity/MixinEntityLivingBaseAnimations.java \
  src/main/resources/mixins.gnuclient.json
git commit -m "Add Animations swing-speed inject on EntityLivingBase."
```

---

### Task 6: Build, stage, vault note, manual smoke

**Files:**
- Modify/create: `gnu client dev/Decision - Animations OpenMyau parity.md` (workspace vault)
- Link from Architecture / Visuals notes if present

- [ ] **Step 1: Full build + stage jar**

```bash
cd "gnuclient recode" && ./gradlew build
cp -f build/libs/gnuclient-*.jar "../GNUClient/install/lib/gnu-client.jar"
```

Expected: `BUILD SUCCESSFUL`, jar updated

- [ ] **Step 2: Manual smoke (after quit + re-inject / mods replace)**

| Check | Pass criteria |
|-------|----------------|
| Module in Visuals, default on | Appears enabled; HUD suffix shows mode |
| Block with sword, Mode Exhibition | Non-vanilla block pose |
| Scale 50 / 150 | FP item size changes |
| SwingSpeed 0 vs 100 | Swing duration changes |
| Module off | Vanilla block + swing |

- [ ] **Step 3: Vault decision note** (short: approach B, default on, disabled-path redirect fix vs OpenMyau)

- [ ] **Step 4: Final commit** (vault if inside repo; otherwise only code already committed)

```bash
git status
# commit any remaining code/docs under gnuclient recode
```

---

## Spec coverage checklist

| Spec requirement | Task |
|------------------|------|
| 16 modes + Scale + SwingSpeed | 2, 4, 5 |
| Default enabled | 2 (`setEnabled(true)`) |
| No AnimationConfig — module direct | 2, 4, 5 |
| ItemRenderer redirect + inject + scale | 4 |
| Swing inject not overwrite | 5 |
| Register module + mixins JSON | 2–5 |
| HUD suffix | 2 |
| Disabled → vanilla transforms | 4 redirect else-branch |
| Verify build / smoke | 6 |

## Placeholder / consistency self-review

- Mode constants in Task 2 must be the same names Task 4 compares against.
- Helper `armSwingAnimationEnd` used by tests and swing mixin.
- SRG name for swing method verified at implement time if compile/runtime fails.
