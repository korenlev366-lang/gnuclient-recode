package gnu.client.module.setting;

import gnu.client.module.modules.combat.VelocityModule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VisibleWhenTest {

    @Test
    public void velocityModeGatesChildVisibility() {
        VelocityModule v = new VelocityModule();
        ModeSetting mode = null;
        SliderSetting horizontal = null;
        SliderSetting jumpChance = null;
        SliderSetting intaveFactor = null;
        for (Setting<?> s : v.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
            else if ("Horizontal %".equals(s.getName()))
                horizontal = (SliderSetting) s;
            else if ("Chance".equals(s.getName()))
                jumpChance = (SliderSetting) s;
            else if ("Intave Factor".equals(s.getName()))
                intaveFactor = (SliderSetting) s;
        }
        assertNotNull(mode);
        assertNotNull(horizontal);
        assertNotNull(jumpChance);
        assertNotNull(intaveFactor);

        mode.setValue(0);
        assertTrue(horizontal.isVisible());
        assertFalse(jumpChance.isVisible());
        assertFalse(intaveFactor.isVisible());

        mode.setValue(1);
        assertFalse(horizontal.isVisible());
        assertTrue(jumpChance.isVisible());
        assertFalse(intaveFactor.isVisible());

        mode.setValue(2);
        assertFalse(horizontal.isVisible());
        assertTrue(jumpChance.isVisible());
        assertTrue(intaveFactor.isVisible());
    }
}
