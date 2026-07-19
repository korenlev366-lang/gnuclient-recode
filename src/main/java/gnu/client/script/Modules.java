package gnu.client.script;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Script-facing {@code modules} accessor — own settings registry plus
 * cross-module enable/settings control via {@link ModuleManager}.
 */
public final class Modules {

    private final String scriptName;
    private final Module owner;
    private final Map<String, Setting<?>> settings = new HashMap<String, Setting<?>>();

    public Modules(String scriptName, Module owner) {
        this.scriptName = scriptName;
        this.owner = owner;
    }

    public String getScriptName() {
        return scriptName;
    }

    // -------------------- own settings --------------------

    public void registerButton(String name, boolean defaultValue) {
        if (owner == null || name == null)
            return;
        BoolSetting setting = new BoolSetting(name, defaultValue);
        owner.addScriptSetting(setting);
        settings.put(name, setting);
    }

    public void registerSlider(String name, float defaultValue, float min, float max) {
        if (owner == null || name == null)
            return;
        SliderSetting setting = new SliderSetting(name, defaultValue, min, max);
        owner.addScriptSetting(setting);
        settings.put(name, setting);
    }

    public void registerMode(String name, int defaultIndex, String... modeNames) {
        if (owner == null || name == null || modeNames == null || modeNames.length == 0)
            return;
        ModeSetting setting = new ModeSetting(name, defaultIndex, Arrays.asList(modeNames));
        owner.addScriptSetting(setting);
        settings.put(name, setting);
    }

    public boolean getButton(String name) {
        Setting<?> setting = settings.get(name);
        if (!(setting instanceof BoolSetting))
            return false;
        return ((BoolSetting) setting).getValue();
    }

    public void setButton(String name, boolean value) {
        Setting<?> setting = settings.get(name);
        if (setting instanceof BoolSetting)
            ((BoolSetting) setting).setValue(value);
    }

    public float getSlider(String name) {
        Setting<?> setting = settings.get(name);
        if (!(setting instanceof SliderSetting))
            return 0f;
        return ((SliderSetting) setting).getValue();
    }

    public void setSlider(String name, float value) {
        Setting<?> setting = settings.get(name);
        if (setting instanceof SliderSetting)
            ((SliderSetting) setting).setValue(value);
    }

    public int getModeIndex(String name) {
        Setting<?> setting = settings.get(name);
        if (!(setting instanceof ModeSetting))
            return 0;
        return ((ModeSetting) setting).getValue();
    }

    public String getMode(String name) {
        Setting<?> setting = settings.get(name);
        if (!(setting instanceof ModeSetting))
            return "";
        return ((ModeSetting) setting).getCurrentMode();
    }

    public void setMode(String name, String modeName) {
        Setting<?> setting = settings.get(name);
        if (!(setting instanceof ModeSetting) || modeName == null)
            return;
        ModeSetting mode = (ModeSetting) setting;
        List<String> modes = mode.getModes();
        for (int i = 0; i < modes.size(); i++) {
            if (modeName.equalsIgnoreCase(modes.get(i))) {
                mode.setValue(i);
                return;
            }
        }
    }

    public void setModeIndex(String name, int index) {
        Setting<?> setting = settings.get(name);
        if (setting instanceof ModeSetting)
            ((ModeSetting) setting).setValue(index);
    }

    public boolean isMode(String name, String modeName) {
        return modeName != null && modeName.equals(getMode(name));
    }

    /** Bind this script module to an LWJGL key code ({@code -1} unbinds). */
    public void bind(int keyCode) {
        if (owner != null)
            owner.setKeyCode(keyCode);
    }

    public int getBind() {
        return owner == null ? -1 : owner.getKeyCode();
    }

    // -------------------- cross-module --------------------

    public List<String> names() {
        List<String> out = new ArrayList<String>();
        for (Module m : ModuleManager.INSTANCE.all())
            out.add(m.getName());
        return out;
    }

    public boolean exists(String moduleName) {
        return find(moduleName) != null;
    }

    public boolean isEnabled(String moduleName) {
        Module m = find(moduleName);
        return m != null && m.isEnabled();
    }

    public boolean enable(String moduleName) {
        Module m = find(moduleName);
        if (m == null)
            return false;
        m.setEnabled(true);
        return true;
    }

    public boolean disable(String moduleName) {
        Module m = find(moduleName);
        if (m == null)
            return false;
        m.setEnabled(false);
        return true;
    }

    public boolean toggle(String moduleName) {
        Module m = find(moduleName);
        if (m == null)
            return false;
        m.toggle();
        return true;
    }

    public boolean setEnabled(String moduleName, boolean enabled) {
        Module m = find(moduleName);
        if (m == null)
            return false;
        m.setEnabled(enabled);
        return true;
    }

    public boolean getBool(String moduleName, String settingName) {
        Setting<?> s = findSetting(moduleName, settingName);
        if (s instanceof BoolSetting)
            return ((BoolSetting) s).getValue();
        return false;
    }

    public boolean setBool(String moduleName, String settingName, boolean value) {
        Setting<?> s = findSetting(moduleName, settingName);
        if (!(s instanceof BoolSetting))
            return false;
        ((BoolSetting) s).setValue(value);
        return true;
    }

    public float getSlider(String moduleName, String settingName) {
        Setting<?> s = findSetting(moduleName, settingName);
        if (s instanceof SliderSetting)
            return ((SliderSetting) s).getValue();
        return 0f;
    }

    public boolean setSlider(String moduleName, String settingName, float value) {
        Setting<?> s = findSetting(moduleName, settingName);
        if (!(s instanceof SliderSetting))
            return false;
        ((SliderSetting) s).setValue(value);
        return true;
    }

    public String getMode(String moduleName, String settingName) {
        Setting<?> s = findSetting(moduleName, settingName);
        if (s instanceof ModeSetting)
            return ((ModeSetting) s).getCurrentMode();
        return "";
    }

    public boolean setMode(String moduleName, String settingName, String modeName) {
        Setting<?> s = findSetting(moduleName, settingName);
        if (!(s instanceof ModeSetting) || modeName == null)
            return false;
        ModeSetting mode = (ModeSetting) s;
        List<String> modes = mode.getModes();
        for (int i = 0; i < modes.size(); i++) {
            if (modeName.equalsIgnoreCase(modes.get(i))) {
                mode.setValue(i);
                return true;
            }
        }
        return false;
    }

    public List<String> settingNames(String moduleName) {
        List<String> out = new ArrayList<String>();
        Module m = find(moduleName);
        if (m == null)
            return out;
        for (Setting<?> s : m.getSettings())
            out.add(s.getName());
        return out;
    }

    private static Module find(String moduleName) {
        if (moduleName == null || moduleName.isEmpty())
            return null;
        return ModuleManager.INSTANCE.getModule(moduleName);
    }

    private static Setting<?> findSetting(String moduleName, String settingName) {
        Module m = find(moduleName);
        if (m == null || settingName == null)
            return null;
        for (Setting<?> s : m.getSettings()) {
            if (settingName.equalsIgnoreCase(s.getName()))
                return s;
        }
        return null;
    }
}
