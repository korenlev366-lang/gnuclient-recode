package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;

public final class GrimTestVelocity extends VelocityMode {

    private boolean hasReceivedVelocity;
    private int jumpCount;

    public GrimTestVelocity(VelocityModule parent) {
        super("Grimtest", parent);
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
                    && mc.currentScreen == null && !player.isPotionActive(Potion.jump)
                    && !parent.isInLiquidOrWeb()) {
                if (jumpCount >= (int) parent.grimReduceJumpLimit.getValue().floatValue()) {
                    jumpCount = 0;
                    hasReceivedVelocity = false;
                } else {
                    jumpCount++;
                    player.movementInput.jump = true;
                }
            } else if (player.hurtTime <= 1) {
                hasReceivedVelocity = false;
                jumpCount = 0;
            }
        }
    }
}
