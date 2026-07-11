package gnu.client.module.modules.combat.velocity;

import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;

public final class VelocityMove {

    private VelocityMove() {}

    public static double getSpeed() {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return 0.0;
        return Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
    }

    public static float getMoveYaw() {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return 0f;
        return adjustYaw(p.rotationYaw, p.movementInput.moveForward, p.movementInput.moveStrafe);
    }

    private static float adjustYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0f) {
            yaw += 180.0f;
        }
        if (strafe != 0.0f) {
            float multiplier = forward == 0.0f ? 1.0f : 0.5f * Math.signum(forward);
            yaw += -90.0f * multiplier * Math.signum(strafe);
        }
        return MathHelper.wrapAngleTo180_float(yaw);
    }

    public static void setSpeed(double speed, float yaw) {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return;
        float rad = (float) Math.toRadians(yaw);
        p.motionX = -Math.sin(rad) * speed;
        p.motionZ = Math.cos(rad) * speed;
    }

    public static boolean isMoving() {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return false;
        return p.moveForward != 0f || p.moveStrafing != 0f;
    }
}
