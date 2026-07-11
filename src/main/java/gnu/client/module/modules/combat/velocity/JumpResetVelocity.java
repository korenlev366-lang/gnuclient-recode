package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

/**
 * OpenMiau JumpReset — jump while grounded at high hurtTime.
 *
 * <p>Must arm jump on {@link #onMoveInput} (inside living update via MovementInputHook).
 * Calling {@code player.jump()} from ClientTick END is too late: physics for that tick
 * already ran.</p>
 */
public final class JumpResetVelocity extends VelocityMode {

    public JumpResetVelocity(VelocityModule parent) {
        super("JumpReset", parent);
    }

    @Override
    public void onMoveInput(MovementInput input) {
        EntityPlayerSP player = Mc.player();
        if (player == null || input == null)
            return;

        if (!player.onGround || player.hurtTime < 9 || parent.isInLiquidOrWeb())
            return;

        int roll = parent.random.nextInt(100) + 1;
        if (roll > (int) parent.chance.getValue().floatValue())
            return;

        input.jump = true;
    }
}
