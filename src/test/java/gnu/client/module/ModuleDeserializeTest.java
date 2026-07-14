package gnu.client.module;

import com.google.gson.JsonObject;
import gnu.client.config.ConfigManager;
import gnu.client.module.setting.BoolSetting;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModuleDeserializeTest {

    @Before
    public void setUp() {
        ConfigManager.setLoading(true);
    }

    @After
    public void tearDown() {
        ConfigManager.setLoading(false);
    }

    @Test
    public void deserializeWithoutKeyCodeRestoreAppliesEnabledTrue() {
        TestModule module = new TestModule();

        module.deserialize(profileJson(true, 99, true), false);

        assertTrue(module.isEnabled());
        assertTrue(module.getEnabledSetting().getValue());
    }

    @Test
    public void deserializeWithoutKeyCodeRestoreAppliesEnabledFalse() {
        TestModule module = new TestModule();
        module.setEnabled(true);

        module.deserialize(profileJson(false, 99, false), false);

        assertFalse(module.isEnabled());
        assertFalse(module.getEnabledSetting().getValue());
    }

    @Test
    public void deserializeWithoutKeyCodeRestoreAppliesBoolSetting() {
        TestModule module = new TestModule();

        module.deserialize(profileJson(true, 99, true), false);

        assertTrue(module.getEnabledSetting().getValue());
    }

    @Test
    public void deserializeWithoutKeyCodeRestorePreservesKeyCode() {
        TestModule module = new TestModule();
        module.setKeyCode(42);

        module.deserialize(profileJson(true, 99, true), false);

        assertEquals(42, module.getKeyCode());
    }

    @Test
    public void deserializeRestoresKeyCode() {
        TestModule module = new TestModule();
        module.setKeyCode(42);

        module.deserialize(profileJson(true, 99, true));

        assertEquals(99, module.getKeyCode());
    }

    private static JsonObject profileJson(boolean enabled, int keyCode, boolean enabledSetting) {
        JsonObject settings = new JsonObject();
        settings.addProperty("Enabled setting", enabledSetting);

        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("keyCode", keyCode);
        root.add("settings", settings);
        return root;
    }

    private static final class TestModule extends Module {
        private final BoolSetting enabledSetting = addSetting(new BoolSetting("Enabled setting", false));

        private TestModule() {
            super("Profile Test Module", "Profile-safe deserialization test module", Category.MISC);
        }

        BoolSetting getEnabledSetting() {
            return enabledSetting;
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }
    }
}
