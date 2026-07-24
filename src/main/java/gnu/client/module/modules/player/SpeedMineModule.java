package gnu.client.module.modules.player;

import gnu.client.mixin.impl.accessors.IAccessorPlayerControllerMP;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.MovingObjectPosition;

/**
 * Speeds up vanilla block break progress (wsamiaw {@code SpeedMine}).
 */
public final class SpeedMineModule extends Module {

    private final SliderSetting speed = addSetting(new SliderSetting("Speed", 15f, 0f, 100f, 1f));
    private final SliderSetting delay = addSetting(new SliderSetting("Delay", 0f, 0f, 4f, 1f));

    public SpeedMineModule() {
        super("SpeedMine", "Speeds up block breaking progress", Category.PLAYER);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public String[] getSuffix() {
        return new String[] { String.format("%d%%", Math.round(speed.getValue())) };
    }

    @Override
    public void onTickStart() {
        if (!Mc.isInGame())
            return;

        PlayerControllerMP controller = Mc.controller();
        if (!(controller instanceof IAccessorPlayerControllerMP) || controller.isInCreativeMode())
            return;

        MovingObjectPosition mop = Mc.objectMouseOver();
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;

        IAccessorPlayerControllerMP acc = (IAccessorPlayerControllerMP) controller;
        acc.setBlockHitDelay(Math.min(acc.getBlockHitDelay(), Math.round(delay.getValue()) + 1));
        if (!acc.getIsHittingBlock())
            return;

        float damageFloor = 0.3f * (speed.getValue() / 100.0f);
        if (acc.getCurBlockDamageMP() < damageFloor)
            acc.setCurBlockDamageMP(damageFloor);
    }
}
