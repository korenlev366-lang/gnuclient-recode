package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.mc.Mc;
import java.util.concurrent.ThreadLocalRandom;

public final class ScaffoldRotations {
    private ScaffoldRotations() {}

    public static int[] clampRange(int min, int max) {
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        min = Math.max(1, Math.min(100, min));
        max = Math.max(1, Math.min(100, max));
        return new int[] { min, max };
    }

    public static int sampleSpeed(int min, int max) {
        int[] r = clampRange(min, max);
        if (r[0] >= r[1])
            return r[0];
        return ThreadLocalRandom.current().nextInt(r[0], r[1] + 1);
    }

    public static float[] stepTowardNoGcd(float by, float bp, float ty, float tp, int speed) {
        float dy = wrap(ty - by);
        float dp = tp - bp;
        float t = Math.min(1f, Math.max(0f, speed / 100f));
        return new float[] { by + dy * t, clampPitch(bp + dp * t) };
    }

    public static float[] stepToward(float baseYaw, float basePitch,
            float targetYaw, float targetPitch, int speed) {
        float[] stepped = stepTowardNoGcd(baseYaw, basePitch, targetYaw, targetPitch, speed);
        return applyGcd(stepped[0], stepped[1], baseYaw, basePitch);
    }

    static float[] applyGcd(float targetYaw, float targetPitch, float yaw, float pitch) {
        targetYaw = yaw + wrap(targetYaw - yaw);
        float deltaYaw = targetYaw - yaw;
        float deltaPitch = targetPitch - pitch;
        double gcd = Mc.getMouseSensitivityGcd();
        if (gcd <= 0.0)
            return new float[] { targetYaw, clampPitch(targetPitch) };
        float qYaw = (float) (Math.round(deltaYaw / gcd) * gcd);
        float qPitch = (float) (Math.round(deltaPitch / gcd) * gcd);
        return new float[] { yaw + qYaw, clampPitch(pitch + qPitch) };
    }

    public static float wrap(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    /** Unwrap {@code to} onto the continuous branch of {@code from} (Grim-safe). */
    public static float continuous(float from, float to) {
        return from + wrap(to - from);
    }

    public static float clampPitch(float p) {
        return Math.max(-90f, Math.min(90f, p));
    }
}
