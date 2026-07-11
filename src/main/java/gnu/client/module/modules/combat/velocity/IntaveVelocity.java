package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

public final class IntaveVelocity extends VelocityMode {

    private boolean attacked;
    private boolean slowDown;

    public IntaveVelocity(VelocityModule parent) {
        super("Intave", parent);
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return;

        if (attacked && !slowDown && player.isSprinting()) {
            player.motionX *= 0.6D;
            player.motionZ *= 0.6D;
            player.setSprinting(false);
        }

        attacked = false;
        slowDown = false;
    }

    @Override
    public void onAttack(Object target) {
        attacked = true;
    }
}
