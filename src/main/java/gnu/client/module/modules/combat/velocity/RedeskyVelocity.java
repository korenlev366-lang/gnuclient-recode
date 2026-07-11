package gnu.client.module.modules.combat.velocity;

import gnu.client.mixin.impl.accessors.IAccessorS12PacketEntityVelocity;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public final class RedeskyVelocity extends VelocityMode {

    public RedeskyVelocity(VelocityModule parent) {
        super("Redesky", parent);
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return false;

        double horizontal = parent.horizontal.getValue();
        double vertical = parent.vertical.getValue();

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() != player.getEntityId())
                return false;

            if (horizontal == 0.0 && vertical == 0.0)
                return true;

            IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) vel;
            accessor.setMotionX((int) (vel.getMotionX() * horizontal / 100.0));
            accessor.setMotionY((int) (vel.getMotionY() * vertical / 100.0));
            accessor.setMotionZ((int) (vel.getMotionZ() * horizontal / 100.0));

            Mc.addToSendQueue(new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.START_SNEAKING));
            Mc.addToSendQueue(new C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SNEAKING));
            return false;
        }

        if (packet instanceof S27PacketExplosion) {
            return horizontal == 0.0 && vertical == 0.0;
        }

        return false;
    }
}
