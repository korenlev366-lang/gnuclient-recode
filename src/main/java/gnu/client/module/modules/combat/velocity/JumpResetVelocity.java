package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

public final class JumpResetVelocity extends VelocityMode {

    public JumpResetVelocity(VelocityModule parent) {
        super("JumpReset", parent);
    }

    @Override
    public void onUpdate(boolean pre) {
        if (pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        if (player.onGround && player.hurtTime >= 9 && !parent.isInLiquidOrWeb()
                && parent.random.nextInt(100) + 1 <= (int) parent.chance.getValue().floatValue()) {
            player.jump();
        }
    }
}
