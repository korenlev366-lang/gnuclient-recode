package gnu.client.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;

/**
 * Silent rotation activation, movefix yaw, and F5/FreeLook body/head render angles.
 *
 * <p>{@link #getSmoothedYaw()} is the <b>move yaw</b> ({@code pervYaw}) for
 * {@code fixStrafe}/{@code moveFlying}. Packet yaw/pitch drive C03 and the
 * rendered head/body via {@link #getRotationYawHead()} / {@link #getRenderYawOffset()}.
 *
 * <p>Body yaw follows the silent/server look (not walk direction). Prev angles advance
 * at most once per client tick so multi-{@link #applyState} calls in one tick do not
 * flatten interpolation for F5.
 */
public final class RotationState {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static int state = -1;
    private static float smoothYaw;
    private static int priority = -1;
    private static int lastApplyTick = Integer.MIN_VALUE;

    private static float prevRenderYawOffset;
    private static float renderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;
    private static float prevRotationPitch;
    private static float rotationPitch;

    private RotationState() {}

    /**
     * Unwrap {@code to} onto the continuous branch of {@code from} so render lerps
     * take the short arc (avoids 179→-179 spinning the long way).
     */
    static float continuousYaw(float from, float to) {
        return from + MathHelper.wrapAngleTo180_float(to - from);
    }

    /**
     * @param active whether silent rotation is active this tick
     * @param yaw packet / look yaw (C03 + rendered head/body)
     * @param pitch packet pitch (C03 + rendered head)
     * @param pervYaw <b>move yaw</b> for fixStrafe / moveFlying
     * @param rotPriority KillAura=1, Scaffold=3 when MoveFix armed; use {@code -1}
     *                    for render-only silent rotations (no moveFlying swap)
     */
    public static void applyState(boolean active, float yaw, float pitch, float pervYaw, int rotPriority) {
        state = active ? 0 : state + 1;
        EntityPlayerSP player = mc.thePlayer;
        int tick = player != null ? player.ticksExisted : Integer.MIN_VALUE;
        // Snapshot prev once per tick so later applyState calls only update current.
        if (tick != lastApplyTick) {
            lastApplyTick = tick;
            prevRenderYawOffset = renderYawOffset;
            prevRotationYawHead = rotationYawHead;
            prevRotationPitch = rotationPitch;
        }
        if (active) {
            // Body + head face server/silent yaw (wrap-continuous from tick start).
            renderYawOffset = continuousYaw(prevRenderYawOffset, yaw);
            rotationYawHead = continuousYaw(prevRotationYawHead, yaw);
            rotationPitch = pitch;
        } else if (player != null) {
            renderYawOffset = continuousYaw(prevRenderYawOffset, player.renderYawOffset);
            rotationYawHead = continuousYaw(prevRotationYawHead, player.rotationYawHead);
            rotationPitch = player.rotationPitch;
        }
        smoothYaw = pervYaw;
        priority = rotPriority;
    }

    public static boolean isActived() {
        return isRotated(0);
    }

    /** True for {@code state} ticks after last active apply (OpenMyau render gate uses 1). */
    public static boolean isRotated(int maxAge) {
        return state >= 0 && state <= maxAge;
    }

    public static float getSmoothedYaw() {
        return smoothYaw;
    }

    public static float getPriority() {
        return priority;
    }

    public static float getPrevRenderYawOffset() {
        return prevRenderYawOffset;
    }

    public static float getRenderYawOffset() {
        return renderYawOffset;
    }

    public static float getPrevRotationYawHead() {
        return prevRotationYawHead;
    }

    public static float getRotationYawHead() {
        return rotationYawHead;
    }

    public static float getPrevRotationPitch() {
        return prevRotationPitch;
    }

    public static float getRotationPitch() {
        return rotationPitch;
    }

    public static void reset() {
        state = -1;
        priority = -1;
        lastApplyTick = Integer.MIN_VALUE;
    }
}
