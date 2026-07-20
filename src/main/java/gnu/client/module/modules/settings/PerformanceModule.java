package gnu.client.module.modules.settings;

import gnu.client.common.GnuLog;
import gnu.client.common.OptiFineCompat;
import gnu.client.config.ConfigManager;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.util.EntityCull;

import java.util.Arrays;

/**
 * General client performance settings — optimizations applied to vanilla Minecraft
 * via mixins (not just this mod's overlays). Modeled after the perf toggles in
 * non-cheat clients: particle/weather reduction, entity render distance,
 * smooth-lighting off, and skipping heavy world passes while a GUI covers the screen.
 *
 * <p>These are feature toggles, not an enable-gated module: the module stays enabled
 * and each setting independently turns its mixin behavior on/off.
 */
public final class PerformanceModule extends Module {

    public static final String NAME = "Performance";

    private static final int PRESET_CUSTOM = 0;
    private static final int PRESET_BALANCED = 1;
    private static final int PRESET_PVP = 2;
    private static final int PRESET_ULTRA = 3;

    private static PerformanceModule instance;

    private boolean applyingPreset;

    private final ModeSetting fpsPreset = addSetting(
            new ModeSetting("FPS Preset", 0, Arrays.asList("Custom", "Balanced", "PvP", "Ultra"))
                    .onChange(this::onFpsPresetChanged));
    private final BoolSetting fastPlayerModels = addSetting(new BoolSetting("Fast Player Models", false));

    // Particles / weather
    private final BoolSetting reducedParticles = addSetting(new BoolSetting("Reduced Particles", false));
    private final SliderSetting particleLimit = addSetting(
            new SliderSetting("Particle Limit", 800f, 0f, 4000f, 50f));
    private final BoolSetting minimalParticles = addSetting(new BoolSetting("Minimal Particles", false)
            .disabledWhen(() -> OptiFineCompat.fastRenderActive()));
    private final BoolSetting clearWeather = addSetting(new BoolSetting("Clear Weather", false));

    // Entities
    private final BoolSetting reducedEntityDistance = addSetting(new BoolSetting("Reduced Entity Distance", false));
    private final SliderSetting entityDistance = addSetting(
            new SliderSetting("Entity Distance", 0.75f, 0.1f, 1.0f, 0.05f));
    private final BoolSetting entityCull = addSetting(new BoolSetting("Entity Cull", false));
    private final ModeSetting cullMode = addSetting(
            new ModeSetting("Cull Mode", 0, Arrays.asList("Lite", "Aggressive"))
                    .visibleWhen(() -> entityCull.isToggled()));

    // World / chunk rendering
    private final BoolSetting reducedRenderDistance = addSetting(new BoolSetting("Reduced Render Distance", false));
    private final SliderSetting renderDistance = addSetting(
            new SliderSetting("Render Distance", 8f, 2f, 16f, 1f));
    private final BoolSetting cloudsOff = addSetting(new BoolSetting("Clouds Off", false));
    private final BoolSetting fastGraphics = addSetting(new BoolSetting("Fast Graphics", false));
    private final BoolSetting forceVbo = addSetting(new BoolSetting("Force VBO", false)
            .disabledWhen(() -> OptiFineCompat.fastRenderActive()));
    private final BoolSetting noEntityNames = addSetting(new BoolSetting("No Entity Names", false)
            .disabledWhen(() -> OptiFineCompat.fastRenderActive()));
    private final SliderSetting nameTagDistance = addSetting(
            new SliderSetting("Name Tag Distance", 64f, 16f, 128f, 4f));
    private final BoolSetting dynamicRenderDistance = addSetting(new BoolSetting("Dynamic Render Distance", false));
    private final SliderSetting dynamicRenderDistanceMin = addSetting(
            new SliderSetting("Dynamic RD Min", 4f, 2f, 16f, 1f));
    private final BoolSetting entityShadowsOff = addSetting(new BoolSetting("Entity Shadows Off", false)
            .disabledWhen(() -> OptiFineCompat.fastRenderActive()));
    private final BoolSetting fboOff = addSetting(new BoolSetting("Disable FBO", false)
            .disabledWhen(() -> OptiFineCompat.fastRenderActive()));
    private final BoolSetting viewBobbingOff = addSetting(new BoolSetting("No View Bobbing", false));
    private final BoolSetting mipmapsOff = addSetting(new BoolSetting("Disable Mipmaps", false)
            .disabledWhen(() -> OptiFineCompat.fastRenderActive()));
    private final BoolSetting limitFps = addSetting(new BoolSetting("Limit FPS", false));
    private final SliderSetting fpsCap = addSetting(
            new SliderSetting("FPS Cap", 120f, 10f, 260f, 5f));

    // Rendering quality
    private final BoolSetting disableSmoothLighting = addSetting(new BoolSetting("Disable Smooth Lighting", false));
    private final BoolSetting skipWorldWhenGuiOpen = addSetting(new BoolSetting("Skip World When GUI Open", false));
    private final BoolSetting skipCloudsWhenGuiOpen = addSetting(new BoolSetting("Skip Clouds When GUI Open", false));
    private final BoolSetting noHurtCam = addSetting(new BoolSetting("No Hurt Cam", false));
    private final BoolSetting boxDisplayLists = addSetting(new BoolSetting("Box Display Lists", true));

    public PerformanceModule() {
        super(NAME, "General client performance (vanilla MC optimizations)", Category.SETTINGS);
        instance = this;
        wirePresetDirtyTracking();
        setEnabled(true);
    }

    private void wirePresetDirtyTracking() {
        Runnable dirty = this::markPresetCustom;
        entityCull.onChange(dirty);
        cullMode.onChange(dirty);
        reducedEntityDistance.onChange(dirty);
        entityDistance.onChange(dirty);
        reducedParticles.onChange(dirty);
        particleLimit.onChange(dirty);
        minimalParticles.onChange(dirty);
        clearWeather.onChange(dirty);
        cloudsOff.onChange(dirty);
        entityShadowsOff.onChange(dirty);
        fastGraphics.onChange(dirty);
        noEntityNames.onChange(dirty);
        noHurtCam.onChange(dirty);
        viewBobbingOff.onChange(dirty);
        skipWorldWhenGuiOpen.onChange(dirty);
        fastPlayerModels.onChange(dirty);
    }

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
        ConfigManager cm = ConfigManager.instance();
        if (cm != null && cm.isLoading())
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
            fpsPreset.setValue(preset);
        } finally {
            applyingPreset = false;
        }
    }

    private void applyBalanced() {
        entityCull.setValue(true);
        cullMode.setValue(0);
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
        cullMode.setValue(1);
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
        reducedParticles.setValue(true);
        fastPlayerModels.setValue(true);
    }

    public static PerformanceModule instance() {
        return instance;
    }

    // ---- accessors read by the performance mixins ----

    public static boolean reducedParticles() {
        return instance != null && instance.reducedParticles.isToggled();
    }

    public static int particleLimit() {
        return instance == null ? 800 : Math.round(instance.particleLimit.getValue());
    }

    public static boolean clearWeather() {
        return instance != null && instance.clearWeather.isToggled();
    }

    /**
     * Forces {@code GameSettings.particleSetting = 2} (Minimal) so only a small subset of
     * particles render — the Sodium-style "do less redundant particle work" lever. Safe
     * with OptiFine present (it exposes the same vanilla setting, no render-path conflict).
     */
    public static boolean minimalParticles() {
        if (!OptiFineCompat.renderPathTweaksAllowed()) {
            warnOptiFineRenderPathOnce();
            return false;
        }
        return instance != null && instance.minimalParticles.isToggled();
    }

    public static boolean reducedEntityDistance() {
        return instance != null && instance.reducedEntityDistance.isToggled();
    }

    /** Fraction of the game's render distance entities are allowed to render at. */
    public static float entityDistanceFraction() {
        return instance == null ? 0.75f : instance.entityDistance.getValue();
    }

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

    public static boolean reducedRenderDistance() {
        return instance != null && instance.reducedRenderDistance.isToggled();
    }

    /** Absolute chunk render distance (in chunks) to clamp the game to. */
    public static int renderDistanceChunks() {
        return instance == null ? 8 : Math.round(instance.renderDistance.getValue());
    }

    public static boolean cloudsOff() {
        if (!OptiFineCompat.smoothLightingToggleAllowed()) {
            warnOptiFineOnce();
            return false;
        }
        return instance != null && instance.cloudsOff.isToggled();
    }

    public static boolean fastGraphics() {
        if (!OptiFineCompat.smoothLightingToggleAllowed()) {
            warnOptiFineOnce();
            return false;
        }
        return instance != null && instance.fastGraphics.isToggled();
    }

    /**
     * Forces {@code GameSettings.useVbo = true} so chunk meshes upload to GPU vertex
     * buffers instead of immediate-mode display lists. Safe only when OptiFine's Fast
     * Render is OFF — with it on, OptiFine owns the render path and this is skipped.
     */
    public static boolean forceVbo() {
        if (!OptiFineCompat.renderPathTweaksAllowed()) {
            warnOptiFineRenderPathOnce();
            return false;
        }
        return instance != null && instance.forceVbo.isToggled();
    }

    public static boolean noEntityNames() {
        return instance != null && instance.noEntityNames.isToggled();
    }

    /** Max distance (blocks) at which entity name tags render; beyond it they're skipped. */
    public static float nameTagDistance() {
        return instance == null ? 64f : instance.nameTagDistance.getValue();
    }

    public static boolean dynamicRenderDistance() {
        return instance != null && instance.dynamicRenderDistance.isToggled();
    }

    /** Floor (in chunks) that dynamic render distance will not drop below. */
    public static int dynamicRenderDistanceMin() {
        return instance == null ? 4 : Math.round(instance.dynamicRenderDistanceMin.getValue());
    }

    public static boolean entityShadowsOff() {
        if (!OptiFineCompat.renderPathTweaksAllowed()) {
            warnOptiFineRenderPathOnce();
            return false;
        }
        return instance != null && instance.entityShadowsOff.isToggled();
    }

    public static boolean fboOff() {
        if (!OptiFineCompat.renderPathTweaksAllowed()) {
            warnOptiFineRenderPathOnce();
            return false;
        }
        return instance != null && instance.fboOff.isToggled();
    }

    public static boolean viewBobbingOff() {
        return instance != null && instance.viewBobbingOff.isToggled();
    }

    public static boolean mipmapsOff() {
        if (!OptiFineCompat.renderPathTweaksAllowed()) {
            warnOptiFineRenderPathOnce();
            return false;
        }
        return instance != null && instance.mipmapsOff.isToggled();
    }

    public static boolean limitFps() {
        return instance != null && instance.limitFps.isToggled();
    }

    public static int fpsCap() {
        return instance == null ? 120 : Math.round(instance.fpsCap.getValue());
    }

    public static boolean disableSmoothLighting() {
        if (!OptiFineCompat.smoothLightingToggleAllowed()) {
            warnOptiFineOnce();
            return false;
        }
        return instance != null && instance.disableSmoothLighting.isToggled();
    }

    private static boolean optiFineWarned;

    private static void warnOptiFineOnce() {
        if (optiFineWarned)
            return;
        optiFineWarned = true;
        GnuLog.log("Performance: OptiFine detected — 'Disable Smooth Lighting' disabled to avoid "
                + "conflicts with OptiFine's lighting pipeline. Other performance toggles remain active.");
    }

    private static boolean optiFineRenderPathWarned;

    private static void warnOptiFineRenderPathOnce() {
        if (optiFineRenderPathWarned)
            return;
        optiFineRenderPathWarned = true;
        GnuLog.log("Performance: OptiFine Fast Render is ON — VBO optimization skipped "
                + "to avoid conflicts with OptiFine's render path. Disable Fast Render in OptiFine to use it.");
    }

    public static boolean skipWorldWhenGuiOpen() {
        return instance != null && instance.skipWorldWhenGuiOpen.isToggled();
    }

    public static boolean skipCloudsWhenGuiOpen() {
        return instance != null && instance.skipCloudsWhenGuiOpen.isToggled();
    }

    public static boolean noHurtCam() {
        return instance != null && instance.noHurtCam.isToggled();
    }

    public static boolean boxDisplayLists() {
        return instance == null || instance.boxDisplayLists.isToggled();
    }

    public static boolean fastPlayerModels() {
        return instance != null && instance.fastPlayerModels.isToggled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(true);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}
}
