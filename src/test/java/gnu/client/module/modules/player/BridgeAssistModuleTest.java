package gnu.client.module.modules.player;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class BridgeAssistModuleTest {

    @Test
    public void ravenDefaultSettings() {
        BridgeAssistModule m = new BridgeAssistModule();
        assertEquals("Bridge Assist", m.getName());

        SliderSetting edge = null, unsneak = null, jump = null;
        BoolSetting pre = null, sneakKey = null, blocks = null, look = null, noFwd = null;
        for (Setting<?> s : m.getSettings()) {
            switch (s.getName()) {
                case "Edge offset": edge = (SliderSetting) s; break;
                case "Unsneak delay": unsneak = (SliderSetting) s; break;
                case "Sneak on jump": jump = (SliderSetting) s; break;
                case "Pre place": pre = (BoolSetting) s; break;
                case "Sneak key pressed": sneakKey = (BoolSetting) s; break;
                case "Holding blocks": blocks = (BoolSetting) s; break;
                case "Looking down": look = (BoolSetting) s; break;
                case "Not moving forward": noFwd = (BoolSetting) s; break;
                default: break;
            }
        }
        assertNotNull(edge);
        assertNotNull(unsneak);
        assertNotNull(jump);
        assertNotNull(pre);
        assertNotNull(sneakKey);
        assertNotNull(blocks);
        assertNotNull(look);
        assertNotNull(noFwd);

        assertEquals(0.0f, edge.getValue(), 0.001f);
        assertEquals(0.0f, edge.getMin(), 0.001f);
        assertEquals(0.3f, edge.getMax(), 0.001f);
        assertEquals(0.01f, edge.getStep(), 0.001f);

        assertEquals(50.0f, unsneak.getValue(), 0.001f);
        assertEquals(50.0f, unsneak.getMin(), 0.001f);
        assertEquals(300.0f, unsneak.getMax(), 0.001f);

        assertEquals(0.0f, jump.getValue(), 0.001f);
        assertEquals(500.0f, jump.getMax(), 0.001f);

        assertFalse(pre.getValue());
        assertFalse(sneakKey.getValue());
        assertFalse(blocks.getValue());
        assertFalse(look.getValue());
        assertFalse(noFwd.getValue());
    }
}
