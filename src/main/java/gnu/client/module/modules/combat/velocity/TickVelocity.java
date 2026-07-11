package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

public final class TickVelocity extends VelocityMode {

    public TickVelocity(VelocityModule parent) {
        super("Tick", parent);
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
            VelocityMove.setSpeed(0.0, VelocityMove.getMoveYaw());
        }
    }
}
