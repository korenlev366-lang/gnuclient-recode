package gnu.client.runtime;

import gnu.client.runtime.mc.McAccess;

/**
 * Shared OpenMyau-style silent movefix helpers used by Scaffold and KillAura.
 * Matches OpenMyau {@code MoveUtil} fixStrafe / isForwardPressed / adjustYaw.
 */
public final class MoveFixUtil {

    public static final int KILLAURA_MOVE_FIX_PRIORITY = 1;
    public static final int SCAFFOLD_MOVE_FIX_PRIORITY = 3;

    private MoveFixUtil() {}

    public static boolean hasMoveFixPriority(int priority) {
        return RotationState.isActived() && RotationState.getPriority() == priority;
    }

    public static boolean isForwardPressed() {
        return McAccess.isForwardKeyHeld() != McAccess.isBackKeyHeld()
            || McAccess.isLeftKeyHeld() != McAccess.isRightKeyHeld();
    }

    /**
     * OpenMyau {@code MoveUtil.fixStrafe} — 8-direction input, ±1 only (Grim-safe).
     * Remaps camera-relative keys to server-yaw-relative forward/strafe.
     */
    public static float[] fixStrafe(float cameraYaw, float serverYaw, boolean sneak) {
        int forwardKey = forwardKeyValue();
        int strafeKey = leftKeyValue();
        if (forwardKey == 0 && strafeKey == 0)
            return new float[] {0.0f, 0.0f};
        float angle = wrapAngle(
            adjustYaw(cameraYaw, forwardKey, strafeKey) - serverYaw + 22.5f);
        float forward;
        float strafe;
        switch (((int) (angle + 180.0f) / 45) % 8) {
            case 0: forward = -1.0f; strafe = 0.0f; break;
            case 1: forward = -1.0f; strafe = 1.0f; break;
            case 2: forward = 0.0f; strafe = 1.0f; break;
            case 3: forward = 1.0f; strafe = 1.0f; break;
            case 4: forward = 1.0f; strafe = 0.0f; break;
            case 5: forward = 1.0f; strafe = -1.0f; break;
            case 6: forward = 0.0f; strafe = -1.0f; break;
            case 7: forward = -1.0f; strafe = -1.0f; break;
            default: forward = 0.0f; strafe = 0.0f; break;
        }
        if (sneak) {
            forward *= 0.3f;
            strafe *= 0.3f;
        }
        return new float[] {forward, strafe};
    }

    public static float adjustYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0f)
            yaw += 180.0f;
        if (strafe != 0.0f) {
            float multiplier = forward == 0.0f ? 1.0f : 0.5f * Math.signum(forward);
            yaw += -90.0f * multiplier * Math.signum(strafe);
        }
        return wrapAngle(yaw);
    }

    /** Camera + WASD → world movement facing (matches OpenMyau {@code adjustYaw} + keys). */
    public static float movementFacingYaw() {
        return adjustYaw(McAccess.getYaw(), forwardKeyValue(), leftKeyValue());
    }

    private static int forwardKeyValue() {
        int value = 0;
        if (McAccess.isForwardKeyHeld())
            value++;
        if (McAccess.isBackKeyHeld())
            value--;
        return value;
    }

    private static int leftKeyValue() {
        int value = 0;
        if (McAccess.isLeftKeyHeld())
            value++;
        if (McAccess.isRightKeyHeld())
            value--;
        return value;
    }

    private static float wrapAngle(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f)
            angle -= 360.0f;
        if (angle < -180.0f)
            angle += 360.0f;
        return angle;
    }
}
