package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

public final class GroundVelocity extends VelocityMode {

    public GroundVelocity(VelocityModule parent) {
        super("Ground", parent);
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        if (parent.onSwing.getValue() && !player.isSwingInProgress)
            return;

        if (player.hurtTime == 9) {
            player.onGround = true;
        }
    }

    @Override
    public void onMoveInput(MovementInput input) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        if (player.hurtTime == 8) {
            input.jump = false;
        }
    }
}
