package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.MovementInput;

public final class LegitVelocity extends VelocityMode {

    private boolean jump;

    public LegitVelocity(VelocityModule parent) {
        super("Legit", parent);
    }

    @Override
    public void onUpdate(boolean pre) {
        if (pre)
            jump = false;
    }

    @Override
    public void onMoveInput(MovementInput input) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return;

        if (jump && VelocityMove.isMoving()
                && parent.random.nextDouble() * 100.0 < parent.chance.getValue()) {
            input.jump = true;
        }
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return false;
        if (!player.onGround)
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() == player.getEntityId() && vel.getMotionY() > 0) {
            jump = true;
        }
        return false;
    }
}
