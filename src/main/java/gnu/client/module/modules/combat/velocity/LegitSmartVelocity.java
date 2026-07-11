package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class LegitSmartVelocity extends VelocityMode {

    private boolean hasReceivedVelocity;
    private int legitSmartJumpCount;

    public LegitSmartVelocity(VelocityModule parent) {
        super("LegitSmart", parent);
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
            if (player.onGround && player.hurtTime >= 8 && player.isSprinting()
                    && !parent.isInLiquidOrWeb()) {
                if (legitSmartJumpCount >= (int) parent.legitSmartJumpLimit.getValue().floatValue()) {
                    legitSmartJumpCount = 0;
                    hasReceivedVelocity = false;
                } else {
                    legitSmartJumpCount++;
                    player.movementInput.jump = true;
                }
            } else if (player.hurtTime <= 1) {
                hasReceivedVelocity = false;
                legitSmartJumpCount = 0;
            }
        }
    }
}
