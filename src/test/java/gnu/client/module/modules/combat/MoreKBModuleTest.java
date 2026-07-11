package gnu.client.module.modules.combat;

import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MoreKBModuleTest {

    @Test
    public void registersTenModesDefaultLegit() {
        MoreKBModule m = new MoreKBModule();
        ModeSetting mode = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
        }
        assertNotNull(mode);
        assertEquals(10, mode.getModes().size());
        assertEquals("Legit", mode.getCurrentMode());
        assertEquals(0, mode.getIndex());
        assertEquals("MoreKB", m.getName());
    }

    @Test
    public void spamSettingsVisibleOnlyOnSpamS() {
        MoreKBModule m = new MoreKBModule();
        ModeSetting mode = null;
        SliderSetting dist = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
            else if ("SpamS Distance".equals(s.getName()))
                dist = (SliderSetting) s;
        }
        assertNotNull(mode);
        assertNotNull(dist);
        mode.setValue(0);
        assertFalse(dist.isVisible());
        mode.setValue(9);
        assertTrue(dist.isVisible());
    }
}
