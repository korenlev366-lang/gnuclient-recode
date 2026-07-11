package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

public final class BounceVelocity extends VelocityMode {

    public BounceVelocity(VelocityModule parent) {
        super("Bounce", parent);
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

        if (player.hurtTime == 9) {
            if (VelocityMove.isMoving()) {
                VelocityMove.setSpeed(VelocityMove.getSpeed(), VelocityMove.getMoveYaw());
            } else {
                player.motionZ *= -1;
                player.motionX *= -1;
            }
        }
    }
}
