# FPS Max Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Performance `FPS Preset` (Custom / Balanced / PvP / Ultra) that applies a known toggle bundle, plus Ultra `Fast Player Models` mixins that skip other players’ armor/held layers.

**Architecture:** Optional `Setting.onChange` fires after value changes. Performance wires covered settings to flip preset → Custom unless `applyingPreset` or config loading. Selecting Balanced/PvP/Ultra calls `applyFpsPreset`. New layer mixins read `fastPlayerModels()`.

**Tech Stack:** Forge 1.8.9, SpongeMixins, existing Performance mixins, JUnit.

**Spec:** `docs/superpowers/specs/2026-07-20-fps-max-presets-design.md`

---

## File map

| File | Responsibility |
|------|----------------|
| `module/setting/Setting.java` | Optional `onChange` callback in `setValue` |
| `module/modules/settings/PerformanceModule.java` | FPS Preset, Fast Player Models, apply + dirty |
| `mixin/impl/render/MixinLayerArmorBase.java` | Cancel armor layer for others |
| `mixin/impl/render/MixinLayerHeldItem.java` | Cancel held-item layer for others |
| `resources/mixins.gnuclient.json` | Register layer mixins |
| `src/test/.../PerformanceFpsPresetTest.java` | apply + dirty→Custom unit tests |

---

### Task 1: Setting.onChange + preset unit tests (TDD)

**Files:**
- Modify: `src/main/java/gnu/client/module/setting/Setting.java`
- Create: `src/test/java/gnu/client/module/modules/settings/PerformanceFpsPresetTest.java`

- [ ] **Step 1: Write failing tests** (PerformanceModule apply not yet wired — tests will fail until Task 2)

```java
package gnu.client.module.modules.settings;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PerformanceFpsPresetTest {

    private PerformanceModule perf;

    @Before
    public void setUp() {
        perf = new PerformanceModule();
    }

    @Test
    public void customDoesNotChangeEntityCull() {
        BoolSetting cull = setting("Entity Cull");
        cull.setValue(false);
        ModeSetting preset = setting("FPS Preset");
        preset.setValue(0); // Custom
        assertFalse(cull.isToggled());
    }

    @Test
    public void balancedEnablesCullLite() {
        ModeSetting preset = setting("FPS Preset");
        preset.setValue(1); // Balanced → must call apply
        assertTrue(((BoolSetting) setting("Entity Cull")).isToggled());
        assertEquals(0, ((ModeSetting) setting("Cull Mode")).getIndex());
        assertFalse(((BoolSetting) setting("Fast Player Models")).isToggled());
        assertTrue(((BoolSetting) setting("Clouds Off")).isToggled());
        assertTrue(((BoolSetting) setting("Clear Weather")).isToggled());
        assertTrue(((BoolSetting) setting("Reduced Particles")).isToggled());
        assertFalse(((BoolSetting) setting("Minimal Particles")).isToggled());
    }

    @Test
    public void pvpEnablesAggressiveAndFastGraphics() {
        ModeSetting preset = setting("FPS Preset");
        preset.setValue(2);
        assertTrue(((BoolSetting) setting("Entity Cull")).isToggled());
        assertEquals(1, ((ModeSetting) setting("Cull Mode")).getIndex());
        assertTrue(((BoolSetting) setting("Fast Graphics")).isToggled());
        assertTrue(((BoolSetting) setting("Skip World When GUI Open")).isToggled());
        assertFalse(((BoolSetting) setting("Fast Player Models")).isToggled());
    }

    @Test
    public void ultraEnablesFastPlayerModels() {
        ModeSetting preset = setting("FPS Preset");
        preset.setValue(3);
        assertTrue(((BoolSetting) setting("Fast Player Models")).isToggled());
        assertEquals(1, ((ModeSetting) setting("Cull Mode")).getIndex());
    }

    @Test
    public void manualEditAfterPresetBecomesCustom() {
        ModeSetting preset = setting("FPS Preset");
        preset.setValue(3);
        assertEquals(3, preset.getIndex());
        ((BoolSetting) setting("Clouds Off")).setValue(false);
        assertEquals(0, preset.getIndex());
    }

    @SuppressWarnings("unchecked")
    private <T extends Setting<?>> T setting(String name) {
        for (Setting<?> s : perf.getSettings()) {
            if (name.equals(s.getName()))
                return (T) s;
        }
        fail("missing setting: " + name);
        return null;
    }
}
```

Note: `ModeSetting.getIndex()` exists — use it. If tests need `ConfigManager.isLoading()`, ensure unit tests don’t NPE (ConfigManager may need instance); if `markCustom` checks ConfigManager, guard with null-safe `instance() == null || !isLoading()`.

- [ ] **Step 2: Add Setting.onChange**

In `Setting.java`:

```java
import java.util.Objects;
import java.util.function.BooleanSupplier;
// add:
private Runnable changeListener;

@SuppressWarnings("unchecked")
public final <S extends Setting<?>> S onChange(Runnable listener) {
    this.changeListener = listener;
    return (S) this;
}

public void setValue(T value) {
    if (Objects.equals(this.value, value))
        return;
    this.value = value;
    if (changeListener != null)
        changeListener.run();
}
```

- [ ] **Step 3: Run tests — expect FAIL** (missing FPS Preset settings / apply)

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.module.modules.settings.PerformanceFpsPresetTest --no-daemon
```

Expected: FAIL (missing settings or apply not hooked).

- [ ] **Step 4: Commit** (only if user asked) — skip by default.

---

### Task 2: PerformanceModule FPS Preset + applyFpsPreset

**Files:**
- Modify: `src/main/java/gnu/client/module/modules/settings/PerformanceModule.java`

- [ ] **Step 1: Add fields** (near top of settings, after NAME / before particles is fine)

```java
private static final int PRESET_CUSTOM = 0;
private static final int PRESET_BALANCED = 1;
private static final int PRESET_PVP = 2;
private static final int PRESET_ULTRA = 3;

private boolean applyingPreset;

private final ModeSetting fpsPreset = addSetting(
        new ModeSetting("FPS Preset", 0, Arrays.asList("Custom", "Balanced", "PvP", "Ultra"))
                .onChange(this::onFpsPresetChanged));

private final BoolSetting fastPlayerModels = addSetting(new BoolSetting("Fast Player Models", false));
```

Wire `fastPlayerModels` and every **covered** setting with `.onChange(this::markPresetCustom)` **after** the fields exist. Covered list from spec matrix:

- entityCull, cullMode, reducedEntityDistance, entityDistance  
- reducedParticles, particleLimit, minimalParticles, clearWeather  
- cloudsOff, entityShadowsOff, fastGraphics, noEntityNames  
- noHurtCam, viewBobbingOff, skipWorldWhenGuiOpen  
- fastPlayerModels  

Do **not** put `onChange(markPresetCustom)` on `fpsPreset` itself (it has `onFpsPresetChanged`).

Because field initializers run in order, either:
1. Add settings without onChange first, then in constructor call `wireDirtyTracking()`, or  
2. Use method refs that only run later (OK) and attach onChange in constructor after all fields init:

```java
public PerformanceModule() {
    super(...);
    instance = this;
    setEnabled(true);
    wirePresetDirtyTracking();
}

private void wirePresetDirtyTracking() {
    Runnable dirty = this::markPresetCustom;
    entityCull.onChange(dirty);
    cullMode.onChange(dirty);
    // ... all covered settings
    fastPlayerModels.onChange(dirty);
}
```

If `onChange` replaces prior listener, set fpsPreset’s listener only once in field init; covered settings get dirty in `wirePresetDirtyTracking`.

- [ ] **Step 2: Implement apply + hooks**

```java
private void onFpsPresetChanged() {
    if (applyingPreset)
        return;
    int idx = fpsPreset.getIndex();
    if (idx == PRESET_CUSTOM)
        return;
    applyFpsPreset(idx);
}

private void markPresetCustom() {
    if (applyingPreset)
        return;
    if (gnu.client.config.ConfigManager.instance() != null
            && gnu.client.config.ConfigManager.instance().isLoading())
        return;
    if (fpsPreset.getIndex() == PRESET_CUSTOM)
        return;
    applyingPreset = true;
    try {
        fpsPreset.setValue(PRESET_CUSTOM);
    } finally {
        applyingPreset = false;
    }
}

/** Package-visible for tests; also used from onFpsPresetChanged. */
void applyFpsPreset(int preset) {
    applyingPreset = true;
    try {
        switch (preset) {
            case PRESET_BALANCED:
                applyBalanced();
                break;
            case PRESET_PVP:
                applyPvp();
                break;
            case PRESET_ULTRA:
                applyUltra();
                break;
            default:
                break;
        }
        fpsPreset.setValue(preset); // keep label (in case called directly)
    } finally {
        applyingPreset = false;
    }
}

private void applyBalanced() {
    entityCull.setValue(true);
    cullMode.setValue(0); // Lite
    reducedEntityDistance.setValue(true);
    entityDistance.setValue(0.75f);
    reducedParticles.setValue(true);
    minimalParticles.setValue(false);
    clearWeather.setValue(true);
    cloudsOff.setValue(true);
    entityShadowsOff.setValue(true);
    fastGraphics.setValue(false);
    noEntityNames.setValue(false);
    noHurtCam.setValue(false);
    viewBobbingOff.setValue(false);
    skipWorldWhenGuiOpen.setValue(false);
    fastPlayerModels.setValue(false);
}

private void applyPvp() {
    entityCull.setValue(true);
    cullMode.setValue(1); // Aggressive
    reducedEntityDistance.setValue(true);
    entityDistance.setValue(0.5f);
    reducedParticles.setValue(false);
    minimalParticles.setValue(true);
    clearWeather.setValue(true);
    cloudsOff.setValue(true);
    entityShadowsOff.setValue(true);
    fastGraphics.setValue(true);
    noEntityNames.setValue(true);
    noHurtCam.setValue(true);
    viewBobbingOff.setValue(true);
    skipWorldWhenGuiOpen.setValue(true);
    fastPlayerModels.setValue(false);
}

private void applyUltra() {
    applyPvp();
    entityDistance.setValue(0.4f);
    particleLimit.setValue(100f);
    minimalParticles.setValue(true);
    reducedParticles.setValue(false);
    fastPlayerModels.setValue(true);
}
```

Accessor:

```java
public static boolean fastPlayerModels() {
    return instance != null && instance.fastPlayerModels.isToggled();
}
```

**Important:** `fpsPreset.setValue(1)` must trigger `onFpsPresetChanged` → `applyFpsPreset`. Because `setValue` skips if equal, first select works. When `applyFpsPreset` sets `fpsPreset.setValue(preset)` again while `applyingPreset`, `onFpsPresetChanged` returns early — good.

When `markPresetCustom` sets Custom, `onFpsPresetChanged` sees CUSTOM and returns without clearing toggles — good (Custom means “don’t auto-change,” not “reset all”).

- [ ] **Step 3: Run tests**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.module.modules.settings.PerformanceFpsPresetTest --no-daemon
```

Expected: PASS. Fix ConfigManager NPEs if any (null-safe isLoading).

- [ ] **Step 4: Commit** (only if user asked).

---

### Task 3: Fast Player Models mixins

**Files:**
- Create: `src/main/java/gnu/client/mixin/impl/render/MixinLayerArmorBase.java`
- Create: `src/main/java/gnu/client/mixin/impl/render/MixinLayerHeldItem.java`
- Modify: `src/main/resources/mixins.gnuclient.json`

- [ ] **Step 1: Armor layer mixin**

```java
package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(LayerArmorBase.class)
public abstract class MixinLayerArmorBase {

    @Inject(method = "doRenderLayer", at = @At("HEAD"), cancellable = true)
    private void gnu$fastPlayerModels(
            EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        if (!PerformanceModule.fastPlayerModels())
            return;
        if (!(entity instanceof EntityPlayer))
            return;
        if (entity == Mc.player())
            return;
        ci.cancel();
    }
}
```

- [ ] **Step 2: Held item layer mixin**

Same pattern targeting `LayerHeldItem`:

```java
@Mixin(LayerHeldItem.class)
public abstract class MixinLayerHeldItem {
    @Inject(method = "doRenderLayer", at = @At("HEAD"), cancellable = true)
    private void gnu$fastPlayerModels(
            EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        if (!PerformanceModule.fastPlayerModels())
            return;
        if (!(entity instanceof EntityPlayer))
            return;
        if (entity == Mc.player())
            return;
        ci.cancel();
    }
}
```

If MCP signature differs, fix from compile errors (common: `LayerArmorBase` uses generic `EntityLivingBase`).

- [ ] **Step 3: Register in mixins.gnuclient.json**

Add after `render.MixinRenderManagerCull`:

```json
    "render.MixinLayerArmorBase",
    "render.MixinLayerHeldItem",
```

- [ ] **Step 4: Compile + remapJar**

```bash
cd "gnuclient recode" && GRADLE_OPTS="-Xmx8g" ./gradlew test --tests gnu.client.module.modules.settings.PerformanceFpsPresetTest compileJava remapJar --no-daemon
```

Expected: SUCCESS.

- [ ] **Step 5: Commit** (only if user asked).

---

### Task 4: Manual verification checklist

**Files:** none (in-game)

- [ ] Custom → no surprise toggles  
- [ ] Balanced → Cull Lite, clouds/weather/shadows/reduced particles; armor visible on others  
- [ ] PvP → Aggressive cull, fast graphics, GUI skip; armor still on others  
- [ ] Ultra → Fast Player Models; others without armor/held item; self OK  
- [ ] Edit Clouds after Ultra → preset label Custom  
- [ ] ESP + KillAura still work  

---

## Spec coverage

| Spec requirement | Task |
|------------------|------|
| FPS Preset Custom/Balanced/PvP/Ultra | Task 2 |
| Applicator writes toggles | Task 2 |
| Dirty → Custom | Task 1 tests + Task 2 |
| Matrix values | Task 2 apply* methods |
| Fast Player Models + mixins | Task 3 |
| Reuse Entity Cull Aggressive | Task 2 (PvP/Ultra) |
| No Phase 3b workers | out of scope |

## Out of scope

- Phase 2 allocations, Phase 3b workers, occlusion, Sodium-style chunking.
