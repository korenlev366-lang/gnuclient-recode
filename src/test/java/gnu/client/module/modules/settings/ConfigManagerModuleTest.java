package gnu.client.module.modules.settings;

import gnu.client.config.ConfigManager;
import gnu.client.module.Category;
import gnu.client.module.setting.BoolSetting;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConfigManagerModuleTest {

    @Before
    public void setUp() {
        ConfigManager.setLoading(true);
    }

    @After
    public void tearDown() {
        ConfigManager.setLoading(false);
    }

    @Test
    public void nameAndCategory() {
        ConfigManagerModule module = new ConfigManagerModule();

        assertEquals("Config Manager", module.getName());
        assertEquals(Category.SETTINGS, module.getCategory());
    }

    @Test
    public void enabledAndAutoSaveDefaults() {
        ConfigManagerModule module = new ConfigManagerModule();

        assertTrue(module.isEnabled());
        assertTrue(ConfigManagerModule.isAutoSaveEnabled());
    }

    @Test
    public void autoSaveSettingIsAvailable() {
        ConfigManagerModule module = new ConfigManagerModule();
        BoolSetting autoSave = null;
        for (Object setting : module.getSettings()) {
            if (setting instanceof BoolSetting && "Auto save".equals(((BoolSetting) setting).getName())) {
                autoSave = (BoolSetting) setting;
            }
        }

        assertNotNull(autoSave);
        assertTrue(autoSave.isToggled());
    }

    @Test
    public void autoSaveEnabledIsNullSafe() throws Exception {
        Field instanceField = ConfigManagerModule.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        ConfigManagerModule previous = (ConfigManagerModule) instanceField.get(null);
        try {
            instanceField.set(null, null);

            assertTrue(ConfigManagerModule.isAutoSaveEnabled());
        } finally {
            instanceField.set(null, previous);
        }
    }

    @Test
    public void setEnabledFalseKeepsEnabled() {
        ConfigManagerModule module = new ConfigManagerModule();

        module.setEnabled(false);

        assertTrue(module.isEnabled());
    }
}
