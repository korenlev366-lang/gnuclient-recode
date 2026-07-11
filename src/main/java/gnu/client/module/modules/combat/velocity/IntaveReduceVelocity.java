package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class IntaveReduceVelocity extends VelocityMode {

    private boolean hasReceivedVelocity;
    private int intaveTick;
    private int intaveDamageTick;

    public IntaveReduceVelocity(VelocityModule parent) {
        super("IntaveReduce", parent);
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() == player.getEntityId()) {
            hasReceivedVelocity = true;
        }
        return false;
    }

    @Override
    public void onUpdate(boolean pre) {
        if (pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        if (hasReceivedVelocity) {
            intaveTick++;
            if (player.hurtTime == 2) {
                intaveDamageTick++;
                if (player.onGround && intaveTick % 2 == 0 && intaveDamageTick <= 10) {
                    player.jump();
                    intaveTick = 0;
                }
                hasReceivedVelocity = false;
            }
        }
    }
}
