package gnu.client.module.modules.combat.velocity;

import gnu.client.mixin.impl.accessors.IAccessorS12PacketEntityVelocity;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class ReverseVelocity extends VelocityMode {

    public ReverseVelocity(VelocityModule parent) {
        super("Reverse", parent);
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) vel;
        accessor.setMotionX((int) (vel.getMotionX() * -0.5));
        accessor.setMotionZ((int) (vel.getMotionZ() * -0.5));
        return false;
    }
}
