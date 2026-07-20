package gnu.client.module.modules.visual;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import org.junit.Test;

import static org.junit.Assert.*;

public class HudModuleWatermarkTest {

    @Test
    public void watermarkSettingDefaultsOn() {
        HudModule hud = new HudModule();
        BoolSetting wm = null;
        for (Setting<?> s : hud.getSettings()) {
            if ("Watermark".equals(s.getName()))
                wm = (BoolSetting) s;
        }
        assertNotNull(wm);
        assertTrue(wm.isToggled());
        assertTrue(hud.wantsWatermark());
    }

    @Test
    public void wantsWatermarkFollowsToggle() {
        HudModule hud = new HudModule();
        BoolSetting wm = find(hud, "Watermark");
        wm.setValue(false);
        assertFalse(hud.wantsWatermark());
        wm.setValue(true);
        assertTrue(hud.wantsWatermark());
    }

    @Test
    public void shouldDrawOverlayWhenOnlyWatermark() {
        HudModule hud = new HudModule();
        hud.setEnabled(true);
        find(hud, "Array").setValue(false);
        find(hud, "Notifications").setValue(false);
        find(hud, "Watermark").setValue(true);
        assertTrue(HudModule.shouldDrawOverlay());
    }

    @Test
    public void shouldNotDrawOverlayWhenHudDisabled() {
        HudModule hud = new HudModule();
        hud.setEnabled(false);
        find(hud, "Watermark").setValue(true);
        assertFalse(HudModule.shouldDrawOverlay());
    }

    private static BoolSetting find(HudModule hud, String name) {
        for (Setting<?> s : hud.getSettings()) {
            if (name.equals(s.getName()))
                return (BoolSetting) s;
        }
        fail("missing " + name);
        return null;
    }
}
