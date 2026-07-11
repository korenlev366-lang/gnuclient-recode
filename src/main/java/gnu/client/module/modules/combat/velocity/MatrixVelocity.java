package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

public final class MatrixVelocity extends VelocityMode {

    public MatrixVelocity(VelocityModule parent) {
        super("Matrix", parent);
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

        if (player.hurtTime > 0) {
            player.motionX *= 0.6D;
            player.motionZ *= 0.6D;
        }
    }
}
