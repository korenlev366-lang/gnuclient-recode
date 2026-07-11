package gnu.client.module.modules.combat.velocity;

import gnu.client.mixin.impl.accessors.IAccessorS12PacketEntityVelocity;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class BufferAbuseVelocity extends VelocityMode {

    private int amount;

    public BufferAbuseVelocity(VelocityModule parent) {
        super("BufferAbuse", parent);
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

        if (amount < 1) {
            amount++;
            return true;
        }

        double horizontal = parent.horizontal.getValue();
        double vertical = parent.vertical.getValue();
        IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) vel;
        accessor.setMotionX((int) (vel.getMotionX() * horizontal / 100.0));
        accessor.setMotionY((int) (vel.getMotionY() * vertical / 100.0));
        accessor.setMotionZ((int) (vel.getMotionZ() * horizontal / 100.0));
        amount = 0;
        return false;
    }
}
