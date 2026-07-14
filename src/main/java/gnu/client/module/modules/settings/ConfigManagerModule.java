package gnu.client.module.modules.settings;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;

public final class ConfigManagerModule extends Module {

    public static final String NAME = "Config Manager";

    private static ConfigManagerModule instance;

    private final BoolSetting autoSave = addSetting(new BoolSetting("Auto save", true));

    public ConfigManagerModule() {
        super(NAME, "Save, load, export, and import config profiles", Category.SETTINGS);
        instance = this;
        setEnabled(true);
    }

    public static ConfigManagerModule instance() {
        return instance;
    }

    public static boolean isAutoSaveEnabled() {
        return instance == null || instance.isAutoSaveSettingEnabled();
    }

    private boolean isAutoSaveSettingEnabled() {
        return autoSave.isToggled();
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
