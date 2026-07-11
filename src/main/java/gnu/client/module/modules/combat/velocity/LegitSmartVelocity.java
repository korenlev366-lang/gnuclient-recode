package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.MovementInput;

/**
 * OpenMiau LegitSmart — after self S12, arm jump via movement input while
 * grounded/sprinting at high hurtTime (capped by jump limit).
 */
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
        if (vel.getEntityID() == player.getEntityId())
            hasReceivedVelocity = true;
        return false;
    }

    @Override
    public void onMoveInput(MovementInput input) {
        EntityPlayerSP player = Mc.player();
        if (player == null || input == null || !hasReceivedVelocity)
            return;

        if (player.onGround && player.hurtTime >= 8 && player.isSprinting()
                && !parent.isInLiquidOrWeb()) {
            if (legitSmartJumpCount >= (int) parent.legitSmartJumpLimit.getValue().floatValue()) {
                legitSmartJumpCount = 0;
                hasReceivedVelocity = false;
            } else {
                legitSmartJumpCount++;
                input.jump = true;
            }
        } else if (player.hurtTime <= 1) {
            hasReceivedVelocity = false;
            legitSmartJumpCount = 0;
        }
    }

    @Override
    public void onDisable() {
        hasReceivedVelocity = false;
        legitSmartJumpCount = 0;
    }
}
