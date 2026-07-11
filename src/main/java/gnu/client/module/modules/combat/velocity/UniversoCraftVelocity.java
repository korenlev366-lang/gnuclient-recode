package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public final class UniversoCraftVelocity extends VelocityMode {

    public UniversoCraftVelocity(VelocityModule parent) {
        super("UniversoCraft", parent);
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return false;

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() != player.getEntityId())
                return false;
            player.motionY += 0.1D - parent.random.nextDouble() / 100.0;
            return true;
        }

        if (packet instanceof S27PacketExplosion) {
            player.motionY += 0.1D - parent.random.nextDouble() / 100.0;
            return true;
        }

        return false;
    }
}
