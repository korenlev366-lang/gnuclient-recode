package gnu.client.module.modules.settings;

import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ClickGuiModuleTest {

    @Test
    public void scaleDefaults() {
        ClickGuiModule m = new ClickGuiModule();
        SliderSetting scale = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Scale".equals(s.getName()))
                scale = (SliderSetting) s;
        }
        assertNotNull(scale);
        assertEquals(1.0f, scale.getValue(), 0.001f);
        assertEquals(0.75f, scale.getMin(), 0.001f);
        assertEquals(1.50f, scale.getMax(), 0.001f);
        assertEquals(0.05f, scale.getStep(), 0.001f);
        assertEquals(1.0f, m.getScale(), 0.001f);
    }
}
