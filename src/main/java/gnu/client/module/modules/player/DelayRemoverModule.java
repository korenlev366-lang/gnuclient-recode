package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.Minecraft;

/**
 * Raven-bS {@code DelayRemover} port — clears left-click cooldown (1.7 hitreg)
 * and optionally jump cooldown ticks each player tick end.
 */
public final class DelayRemoverModule extends Module {

    private final BoolSetting oldReg = addSetting(new BoolSetting("1.7 hitreg", true));
    private final BoolSetting removeJumpTicks = addSetting(new BoolSetting("Remove jump ticks", false));

    public DelayRemoverModule() {
        super("Delay Remover", "Removes left-click and jump delay", Category.PLAYER);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void onTick() {
        Minecraft mc = Mc.mc();
        if (mc == null || !mc.inGameHasFocus || !Mc.isInGame())
            return;

        if (oldReg.getValue())
            Mc.clearLeftClickCounter();

        if (removeJumpTicks.getValue())
            Mc.clearJumpTicks(Mc.player());
    }
}
