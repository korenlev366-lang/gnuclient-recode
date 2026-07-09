package gnu.client.runtime;

/**
 * OpenMyau movefix physics gate for {@code moveFlying} / jump.
 *
 * <p>OpenMyau swaps yaw whenever {@link RotationState#isActived()}, but their
 * {@code getSmoothedYaw()} stays at camera yaw unless {@code setPervRotation}
 * ran. Our {@link RotationState#applyState} always writes the silent packet yaw
 * into smoothYaw, so we must require KillAura (1) / Scaffold (3) priority —
 * i.e. MoveFix actually armed — or Simulation fires from silent yaw + camera WASD.
 */
public final class MoveFixHook {

    private MoveFixHook() {}

    public static boolean shouldUseServerMoveYaw() {
        if (!RotationState.isActived())
            return false;
        int priority = (int) RotationState.getPriority();
        return priority == MoveFixUtil.KILLAURA_MOVE_FIX_PRIORITY
            || priority == MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY;
    }
}
