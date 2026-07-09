package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;

/**
 * Toggle gate for Raven-bS AntiBot filtering across combat and visual consumers.
 *
 * <p>Enable this module to activate {@link RavenAntiBot#isBot}. Consumers use
 * {@link gnu.client.runtime.mc.Mc#getWorldEntitiesFiltered} and/or call
 * {@code RavenAntiBot.isBot} inline (KillAura BotCheck, NameTags, etc.).</p>
 */
public final class AntiBotModule extends Module {

    private final BoolSetting tablistCheck = addSetting(new BoolSetting("Tablist", true));

    public AntiBotModule() {
        super("AntiBot", "Filters Raven AntiBot entities from target and visual scans", Category.COMBAT);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    public static boolean isActive() {
        Module module = ModuleManager.INSTANCE.getModule("AntiBot");
        return module != null && module.isEnabled();
    }

    public static boolean isTablistCheckEnabled() {
        Module module = ModuleManager.INSTANCE.getModule("AntiBot");
        if (!(module instanceof AntiBotModule) || !module.isEnabled())
            return false;
        return ((AntiBotModule) module).tablistCheck.getValue();
    }
}
