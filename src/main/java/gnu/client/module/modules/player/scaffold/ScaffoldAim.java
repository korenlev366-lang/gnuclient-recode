package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Aim-mode yaw/pitch targets for scaffold place. */
public final class ScaffoldAim {
    public static final int AIM_BACKWARDS = 0;
    public static final int AIM_GODBRIDGE = 1;
    public static final int AIM_NEAREST = 2;
    public static final int AIM_SIDEWAYS = 3;

    private static final float SIDEWAYS_STEP = 5f;

    private ScaffoldAim() {}

    public static float backwardsYaw(float moveYaw) {
        return moveYaw + 180f;
    }

    /** Diagonal: +45 if preferRight else -45 from moveYaw. */
    public static float godBridgeYaw(float moveYaw, boolean preferRight) {
        return moveYaw + (preferRight ? 45f : -45f);
    }

    /**
     * Prefer right (+45) when D held without A; left when A without D; else default right.
     */
    public static boolean preferGodBridgeRight() {
        if (Mc.isRightKeyHeld() && !Mc.isLeftKeyHeld())
            return true;
        if (Mc.isLeftKeyHeld() && !Mc.isRightKeyHeld())
            return false;
        return true;
    }

    /**
     * Nearest point on the support face rectangle to {@code eye} (axis-aligned clamp).
     */
    public static Vec3 nearestPointOnFace(BlockPos support, EnumFacing face, Vec3 eye) {
        if (support == null || face == null || eye == null)
            return null;
        double minX = support.getX();
        double maxX = support.getX() + 1.0;
        double minY = support.getY();
        double maxY = support.getY() + 1.0;
        double minZ = support.getZ();
        double maxZ = support.getZ() + 1.0;
        double x = MathHelper.clamp_double(eye.xCoord, minX, maxX);
        double y = MathHelper.clamp_double(eye.yCoord, minY, maxY);
        double z = MathHelper.clamp_double(eye.zCoord, minZ, maxZ);
        switch (face) {
            case DOWN:
                y = minY;
                break;
            case UP:
                y = maxY;
                break;
            case NORTH:
                z = minZ;
                break;
            case SOUTH:
                z = maxZ;
                break;
            case WEST:
                x = minX;
                break;
            case EAST:
                x = maxX;
                break;
            default:
                break;
        }
        return new Vec3(x, y, z);
    }

    /**
     * @param aimMode AIM_* constant
     * @param moveYaw movement-facing yaw
     * @param hitPrefer preferred hit on face (may be null → face center)
     */
    public static float[] compute(int aimMode, float moveYaw, float baseYaw, float basePitch,
            EntityPlayer player, ScaffoldTarget target, Vec3 hitPrefer) {
        if (player == null || target == null || target.support == null || target.face == null)
            return new float[] { baseYaw, basePitch };

        Vec3 eye = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 aimPoint = hitPrefer != null
                ? hitPrefer
                : ScaffoldPlace.faceCenter(target.support, target.face);

        switch (aimMode) {
            case AIM_NEAREST: {
                Vec3 nearest = nearestPointOnFace(target.support, target.face, eye);
                if (nearest == null)
                    nearest = aimPoint;
                return ScaffoldPlace.rotationsTo(nearest, player, baseYaw, basePitch);
            }
            case AIM_GODBRIDGE:
                return computeGodBridge(moveYaw, baseYaw, basePitch, player, target, aimPoint);
            case AIM_SIDEWAYS:
                return computeSideways(moveYaw, baseYaw, basePitch, player, target, aimPoint);
            case AIM_BACKWARDS:
            default: {
                float yaw = backwardsYaw(moveYaw);
                float pitch = pitchToward(aimPoint, player, baseYaw, basePitch);
                return new float[] { yaw, pitch };
            }
        }
    }

    private static float[] computeGodBridge(float moveYaw, float baseYaw, float basePitch,
            EntityPlayer player, ScaffoldTarget target, Vec3 aimPoint) {
        float pitch = pitchToward(aimPoint, player, baseYaw, basePitch);
        float yawRight = godBridgeYaw(moveYaw, true);
        float yawLeft = godBridgeYaw(moveYaw, false);
        boolean hitRight = ScaffoldPlace.findPlacementHit(
                player, target.support, target.face, yawRight, pitch) != null;
        boolean hitLeft = ScaffoldPlace.findPlacementHit(
                player, target.support, target.face, yawLeft, pitch) != null;
        float yaw;
        if (hitRight && !hitLeft)
            yaw = yawRight;
        else if (hitLeft && !hitRight)
            yaw = yawLeft;
        else
            yaw = godBridgeYaw(moveYaw, preferGodBridgeRight());
        return new float[] { yaw, pitch };
    }

    private static float[] computeSideways(float moveYaw, float baseYaw, float basePitch,
            EntityPlayer player, ScaffoldTarget target, Vec3 aimPoint) {
        float pitch = pitchToward(aimPoint, player, baseYaw, basePitch);
        float bestYaw = backwardsYaw(moveYaw);
        float bestAbs = -1f;
        for (float offset = -180f; offset <= 180f; offset += SIDEWAYS_STEP) {
            float yaw = moveYaw + offset;
            if (ScaffoldPlace.findPlacementHit(
                    player, target.support, target.face, yaw, pitch) == null)
                continue;
            float abs = Math.abs(ScaffoldRotations.wrap(yaw - moveYaw));
            if (abs > bestAbs) {
                bestAbs = abs;
                bestYaw = yaw;
            }
        }
        return new float[] { bestYaw, pitch };
    }

    private static float pitchToward(Vec3 point, EntityPlayer player, float baseYaw, float basePitch) {
        if (point == null)
            return basePitch;
        return ScaffoldPlace.rotationsTo(point, player, baseYaw, basePitch)[1];
    }
}
