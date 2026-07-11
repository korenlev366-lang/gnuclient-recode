package gnu.client.module.modules.combat;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DisplaceModuleTest {

    @Test
    public void nameAndDefaultSettings() {
        DisplaceModule m = new DisplaceModule();
        assertEquals("Displace", m.getName());

        ModeSetting dynamic = null, direction = null;
        SliderSetting yaw = null, delay = null;
        BoolSetting findVoid = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Dynamic Angle".equals(s.getName())) dynamic = (ModeSetting) s;
            else if ("Direction".equals(s.getName())) direction = (ModeSetting) s;
            else if ("Yaw Offset".equals(s.getName())) yaw = (SliderSetting) s;
            else if ("Delay ms".equals(s.getName())) delay = (SliderSetting) s;
            else if ("Find Void".equals(s.getName())) findVoid = (BoolSetting) s;
        }
        assertNotNull(dynamic);
        assertEquals(0, dynamic.getIndex()); // STATIC
        assertEquals("STATIC", dynamic.getCurrentMode());
        assertNotNull(direction);
        assertEquals("LEFT", direction.getCurrentMode());
        assertNotNull(yaw);
        assertEquals(90.0f, yaw.getValue(), 0.001f);
        assertTrue(yaw.isVisible());
        assertNotNull(delay);
        assertEquals(0.0f, delay.getValue(), 0.001f);
        assertNotNull(findVoid);
        assertTrue(findVoid.isVisible());

        dynamic.setValue(1); // DYNAMIC
        assertFalse(yaw.isVisible());
        assertFalse(direction.isVisible());
        assertFalse(findVoid.isVisible());
    }

    @Test
    public void suffixShowsDelayMs() {
        DisplaceModule m = new DisplaceModule();
        SliderSetting delay = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Delay ms".equals(s.getName())) delay = (SliderSetting) s;
        }
        assertNotNull(delay);
        delay.setValue(100.0f);
        String[] suffix = m.getSuffix();
        assertEquals(1, suffix.length);
        assertEquals("100ms", suffix[0]);
    }
}
