package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

public final class LegitTestVelocity extends VelocityMode {

    private boolean shouldJump;
    private int jumpCooldown;

    public LegitTestVelocity(VelocityModule parent) {
        super("LegitTest", parent);
    }

    @Override
    public void onUpdate(boolean pre) {
        if (pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        int hurtTime = player.hurtTime;

        if (hurtTime >= 8) {
            if (jumpCooldown <= 0) {
                shouldJump = true;
                jumpCooldown = 2;
            }
        } else if (hurtTime <= 1) {
            shouldJump = false;
            jumpCooldown = 0;
        }

        if (shouldJump && player.onGround && jumpCooldown <= 0) {
            player.jump();
            shouldJump = false;
        }

        if (jumpCooldown > 0) {
            jumpCooldown--;
        }
    }
}
