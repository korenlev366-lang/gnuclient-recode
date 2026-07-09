package gnu.client.module.impl.client;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;

/** Client-wide toggles used by rotation and combat utilities (Raven parity subset). */
public final class Settings extends Module {

    public static BoolSetting rotateBody;
    public static BoolSetting fullBody;
    public static SliderSetting randomYawFactor;

    public static BoolSetting showHealthAsHearts;
    public static BoolSetting showHeartSymbol;

    public static BoolSetting weaponAxe;
    public static BoolSetting weaponHoe;
    public static BoolSetting weaponRod;
    public static BoolSetting weaponShovel;
    public static BoolSetting weaponStick;

    public Settings() {
        super("Settings", "Client settings", Category.SETTINGS);
        rotateBody = addSetting(new BoolSetting("Rotate body", true));
        fullBody = addSetting(new BoolSetting("Full body", false));
        randomYawFactor = addSetting(new SliderSetting("Random yaw factor", 0.0f, 0.0f, 10.0f, 1.0f));
        showHealthAsHearts = addSetting(new BoolSetting("Show health as hearts", false));
        showHeartSymbol = addSetting(new BoolSetting("Show heart symbol", false));
        weaponAxe = addSetting(new BoolSetting("Weapon axe", false));
        weaponHoe = addSetting(new BoolSetting("Weapon hoe", false));
        weaponRod = addSetting(new BoolSetting("Weapon rod", false));
        weaponShovel = addSetting(new BoolSetting("Weapon shovel", false));
        weaponStick = addSetting(new BoolSetting("Weapon stick", true));
        setEnabled(true);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(true);
    }
}
