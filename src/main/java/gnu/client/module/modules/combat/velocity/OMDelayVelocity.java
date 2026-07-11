package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class OMDelayVelocity extends VelocityMode {

    private boolean delayActive;
    private boolean reverseFlag;

    public OMDelayVelocity(VelocityModule parent) {
        super("OMDelay", parent);
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null || mc.theWorld == null)
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        // LongJump disabled — treat as not blocking delay
        if (!reverseFlag && !parent.canDelay() && !parent.isInLiquidOrWeb()) {
            parent.delayChanceCounter = parent.delayChanceCounter % 100
                    + (int) parent.delayChance.getValue().floatValue();
            if (parent.delayChanceCounter >= 100) {
                parent.delayQueue.startDelay(player.ticksExisted);
                parent.delayQueue.offer(packet);
                reverseFlag = true;
                delayActive = true;
                return true;
            }
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

        if (reverseFlag && (parent.canDelay() || parent.isInLiquidOrWeb()
                || parent.delayQueue.ticksHeld(player.ticksExisted) >= (long) parent.delayTicks.getValue().floatValue())) {
            parent.delayQueue.stopDelayAndFlush();
            reverseFlag = false;
        }
        if (delayActive) {
            VelocityMove.setSpeed(VelocityMove.getSpeed(), VelocityMove.getMoveYaw());
            delayActive = false;
        }
    }
}
