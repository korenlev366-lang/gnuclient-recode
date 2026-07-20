package gnu.client.module.modules.settings;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
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
        assertEquals(0.4f, ((SliderSetting) setting("Entity Distance")).getValue(), 0.001f);
        assertEquals(100f, ((SliderSetting) setting("Particle Limit")).getValue(), 0.001f);
        assertTrue(((BoolSetting) setting("Reduced Particles")).isToggled());
        assertTrue(((BoolSetting) setting("Minimal Particles")).isToggled());
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
