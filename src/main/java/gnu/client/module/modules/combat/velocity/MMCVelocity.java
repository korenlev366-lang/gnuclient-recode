package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class MMCVelocity extends VelocityMode {

    public MMCVelocity(VelocityModule parent) {
        super("MMC", parent);
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        if (player.hurtTime > 0) {
            player.motionX *= 0.6D;
            player.motionZ *= 0.6D;
        }
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        if (player.isSprinting()) {
            Mc.setSprintKeyState(false);
        }
        return false;
    }
}
