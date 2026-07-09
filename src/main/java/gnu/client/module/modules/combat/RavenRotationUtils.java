package gnu.client.module.modules.combat;

import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Raven-bS {@code RotationUtils} subset for AimAssist.
 * Hot-path math is pure Java — avoid per-call MathHelper reflection.
 */
final class RavenRotationUtils {

    private static final float FAR_THRESHOLD = 180.0f;

    /** Per-tick eye-position cache (invalidated when player ticksExisted changes). */
    private static int eyeCacheTick = Integer.MIN_VALUE;
    private static Entity eyeCachePlayer;
    private static double[] eyeCache;

    private RavenRotationUtils() {}

    /** Call once per AimAssist/KillAura tick before distance/FOV/LOS helpers. */
    static void beginTick(Entity player) {
        if (player == null) {
            eyeCacheTick = Integer.MIN_VALUE;
            eyeCachePlayer = null;
            eyeCache = null;
            return;
        }
        int tick = player.ticksExisted;
        if (tick == eyeCacheTick && player == eyeCachePlayer && eyeCache != null)
            return;
        eyeCacheTick = tick;
        eyeCachePlayer = player;
        eyeCache = eyePosUncached(player, 1.0f);
    }

    static float wrapAngleTo180(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f)
            angle -= 360.0f;
        if (angle < -180.0f)
            angle += 360.0f;
        return angle;
    }

    static float clampPitch(float pitch) {
        if (pitch > 90.0f)
            return 90.0f;
        if (pitch < -90.0f)
            return -90.0f;
        return pitch;
    }

    static float angleToEntity(Entity entity) {
        EntityPlayerSP player = Mc.player();
        if (player == null || entity == null)
            return 0.0f;
        return (float) (Math.atan2(
                entity.posX - player.posX,
                entity.posZ - player.posZ) * 57.2957795 * -1.0);
    }

    /** Cheap center-to-center distance² — use for candidate sorting, not hitbox edge. */
    static double distanceSqCenters(Entity entity) {
        EntityPlayerSP player = Mc.player();
        if (player == null || entity == null)
            return Double.MAX_VALUE;
        double dx = entity.posX - player.posX;
        double dy = entity.posY - player.posY;
        double dz = entity.posZ - player.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    static boolean inFov(float originYaw, float fov, float targetAngle) {
        float half = fov * 0.5f;
        double diff = wrapAngleTo180(originYaw - targetAngle);
        if (diff > 0.0)
            return diff <= half;
        return diff >= -half;
    }

    static double distanceSqFromEyeToClosestOnAabb(Entity entity) {
        double[] eye = eyePos(Mc.player(), 1.0f);
        AxisAlignedBB bb = expandedBox(entity);
        if (eye == null || bb == null)
            return Double.MAX_VALUE;
        if (isVecInside(bb, eye[0], eye[1], eye[2]))
            return 0.0;
        double[] closest = closestPointOnAabb(eye[0], eye[1], eye[2], bb);
        double dx = eye[0] - closest[0];
        double dy = eye[1] - closest[1];
        double dz = eye[2] - closest[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * OpenMyau {@code RotationUtil.rayTrace(box, yaw, pitch, distance)} — intercept of
     * look ray with the entity's expanded collision box. Null = Grim Hitboxes miss.
     */
    static MovingObjectPosition rayTraceHitbox(Entity entity, float yaw, float pitch, double distance) {
        if (entity == null || distance <= 0.0)
            return null;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return null;
        AxisAlignedBB bb = expandedBox(entity);
        if (bb == null)
            return null;
        Vec3 eye = player.getPositionEyes(1.0f);
        if (eye == null)
            return null;
        if (isVecInside(bb, eye.xCoord, eye.yCoord, eye.zCoord))
            return new MovingObjectPosition(entity, eye);
        float f = (float) Math.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = (float) Math.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = (float) -Math.cos(-pitch * 0.017453292F);
        float f3 = (float) Math.sin(-pitch * 0.017453292F);
        Vec3 look = new Vec3(f1 * f2, f3, f * f2);
        Vec3 end = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return bb.calculateIntercept(eye, end);
    }

    /** True if look (yaw/pitch) intersects the target hitbox within {@code distance}. */
    static boolean looksAtHitbox(Entity entity, float yaw, float pitch, double distance) {
        return rayTraceHitbox(entity, yaw, pitch, distance) != null;
    }

    /** OpenMyau {@code RotationUtil.angleToEntity} — eye inside expanded AABB → always in FOV. */
    static boolean isEyeInsideExpandedHitbox(Entity entity) {
        double[] eye = eyePos(Mc.player(), 1.0f);
        AxisAlignedBB bb = expandedBox(entity);
        return eye != null && bb != null && isVecInside(bb, eye[0], eye[1], eye[2]);
    }

    static double aimDifference(Entity entity, float baseYaw) {
        return ((baseYaw - angleToEntity(entity)) % 360.0 + 540.0) % 360.0 - 180.0;
    }

    static double pitchDifference(Entity entity, float basePitch) {
        double[] eye = eyePos(Mc.player(), 1.0f);
        AxisAlignedBB bb = expandedBox(entity);
        if (eye == null || bb == null)
            return 180.0;
        double centerY = (bb.minY + bb.maxY) * 0.5;
        double dx = entity.posX - eye[0];
        double dz = entity.posZ - eye[2];
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = (float) (-(Math.atan2(centerY - eye[1], horiz) * 57.2957795));
        return ((basePitch - targetPitch) % 360.0 + 540.0) % 360.0 - 180.0;
    }

    /** Raven-bS {@code smoothRotation} — legacy 1–30 speed scale. */
    static float[] smoothRotation(float baseYaw, float basePitch,
            float targetYaw, float targetPitch,
            int speed, float randomizationPercent) {
        float smoothedYaw = smoothAxisRaven(baseYaw, targetYaw, speed, randomizationPercent, true);
        float smoothedPitch = smoothAxisRaven(basePitch, targetPitch, speed, randomizationPercent, false);
        return fixRotation(smoothedYaw, smoothedPitch, baseYaw, basePitch);
    }

    /** Timewarp Normal mode — separate H/V steps as percent-of-target per tick (0–100). */
    static float[] smoothRotationHv(float baseYaw, float basePitch,
            float targetYaw, float targetPitch,
            int hSpeed, int vSpeed, float randomizationPercent) {
        float smoothedYaw = smoothAxisPercent(baseYaw, targetYaw, hSpeed, randomizationPercent, true);
        float smoothedPitch = smoothAxisPercent(basePitch, targetPitch, vSpeed, randomizationPercent, false);
        return fixRotation(smoothedYaw, smoothedPitch, baseYaw, basePitch);
    }

    /** Timewarp Advanced mode — direct H/V interpolation with GCD snap. */
    static float[] advancedRotationHv(float baseYaw, float basePitch,
            float targetYaw, float targetPitch,
            int hSpeed, int vSpeed, float randomizationPercent) {
        float deltaYaw = wrapAngleTo180(targetYaw - baseYaw);
        float deltaPitch = targetPitch - basePitch;
        float jitter = randomizationMultiplier(randomizationPercent);
        float tH = Math.min(1.0f, Math.max(0.0f, hSpeed / 100.0f) * jitter);
        float tV = Math.min(1.0f, Math.max(0.0f, vSpeed / 100.0f) * jitter);
        float newYaw = baseYaw + deltaYaw * tH;
        float newPitch = basePitch + deltaPitch * tV;
        return fixRotation(newYaw, newPitch, baseYaw, basePitch);
    }

    private static float smoothAxisPercent(float base, float target, int speedPercent,
            float randomizationPercent, boolean yaw) {
        if (speedPercent <= 0)
            return yaw ? base : clampPitch(base);

        float delta = yaw ? wrapAngleTo180(target - base) : (target - base);
        float magnitude = Math.abs(delta);
        if (magnitude < 0.001f)
            return yaw ? target : clampPitch(target);

        float t = Math.min(1.0f, speedPercent / 100.0f);
        t *= randomizationMultiplier(randomizationPercent);

        float smoothed = base + delta * t;
        return yaw ? smoothed : clampPitch(smoothed);
    }

    private static float smoothAxisRaven(float base, float target, int speed,
            float randomizationPercent, boolean yaw) {
        if (speed <= 0)
            return yaw ? base : clampPitch(base);

        float delta = yaw ? wrapAngleTo180(target - base) : (target - base);
        float magnitude = Math.abs(delta);
        if (magnitude < 0.001f)
            return yaw ? target : clampPitch(target);

        float smoothed = target;
        if (speed < 30) {
            float t = speed / 30.0f;
            float stepSize = t * t * 180.0f;
            stepSize *= randomizationMultiplier(randomizationPercent);

            float stepLength = Math.min(stepSize, magnitude);
            float scale = stepLength / magnitude;
            smoothed = base + delta * scale;
        }
        return yaw ? smoothed : clampPitch(smoothed);
    }

    static float randomizationMultiplier(float randomizationPercent) {
        float range = 0.6f * (randomizationPercent / 100.0f);
        if (range <= 0.001f)
            return 1.0f;
        return 1.0f - range / 2.0f + (float) (Math.random() * range);
    }

    static float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + ((((yaw - prevYaw + 180.0f) % 360.0f) + 360.0f) % 360.0f - 180.0f);
    }

    static float[] fixRotation(float targetYaw, float targetPitch, float yaw, float pitch) {
        targetYaw = unwrapYaw(targetYaw, yaw);
        float deltaYaw = targetYaw - yaw;
        float deltaPitch = targetPitch - pitch;
        double gcd = Mc.getMouseSensitivityGcd();
        if (gcd <= 0.0)
            return new float[] { targetYaw, clampPitch(targetPitch) };

        float qYaw = (float) (Math.round(deltaYaw / gcd) * gcd);
        float qPitch = (float) (Math.round(deltaPitch / gcd) * gcd);
        return new float[] { yaw + qYaw, clampPitch(pitch + qPitch) };
    }

    static float[] getRotationsToTarget(Entity entity, int speed,
            double horizontalMultipoint, double verticalMultipoint,
            float randomizationPercent, boolean useBackup, double range,
            boolean allowThroughBlocks, boolean allowThroughEntities) {
        return getRotationsToTargetTw(entity, speed, speed, false,
                horizontalMultipoint, verticalMultipoint, randomizationPercent,
                useBackup, range, allowThroughBlocks, allowThroughEntities);
    }

    static float[] getRotationsToTargetTw(Entity entity, int hSpeed, int vSpeed, boolean advanced,
            double horizontalMultipoint, double verticalMultipoint,
            float randomizationPercent, boolean useBackup, double range,
            boolean allowThroughBlocks, boolean allowThroughEntities) {
        EntityPlayerSP player = Mc.player();
        if (player == null || entity == null)
            return null;

        float baseYaw = player.rotationYaw;
        float basePitch = player.rotationPitch;
        float[] target = useBackup
                ? getRotationsWithBackup(entity, horizontalMultipoint, verticalMultipoint,
                        baseYaw, basePitch, range, allowThroughBlocks, allowThroughEntities)
                : getRotations(entity, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch);
        if (target == null)
            return null;
        if (advanced)
            return advancedRotationHv(baseYaw, basePitch, target[0], target[1], hSpeed, vSpeed, randomizationPercent);
        return smoothRotationHv(baseYaw, basePitch, target[0], target[1], hSpeed, vSpeed, randomizationPercent);
    }

    static float[] getRawRotationsToTarget(Entity entity,
            double horizontalMultipoint, double verticalMultipoint,
            boolean useBackup, double range,
            boolean allowThroughBlocks, boolean allowThroughEntities) {
        EntityPlayerSP player = Mc.player();
        if (player == null || entity == null)
            return null;
        float baseYaw = player.rotationYaw;
        float basePitch = player.rotationPitch;
        return useBackup
                ? getRotationsWithBackup(entity, horizontalMultipoint, verticalMultipoint,
                        baseYaw, basePitch, range, allowThroughBlocks, allowThroughEntities)
                : getRotations(entity, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch);
    }

    static float[] getRotations(Entity entity, double horizontalMultipoint, double verticalMultipoint,
            float baseYaw, float basePitch) {
        double[] aim = getAimPoint(entity, horizontalMultipoint, verticalMultipoint);
        if (aim == null)
            return null;
        return getRotationsToPoint(aim[0], aim[1], aim[2], baseYaw, basePitch);
    }

    static float[] getRotationsToPoint(double x, double y, double z, float baseYaw, float basePitch) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return null;

        double deltaX = x - player.posX;
        double deltaZ = z - player.posZ;
        double deltaY = y - (player.posY + eyeHeight(player));
        double horizDistSq = deltaX * deltaX + deltaZ * deltaZ;

        float yaw;
        float targetPitch;
        if (horizDistSq < 1.0E-12) {
            yaw = baseYaw;
            targetPitch = (float) (-(Math.atan2(deltaY, 0.0) * 57.2957795));
        } else {
            float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 57.2957795) - 90.0f;
            yaw = baseYaw + wrapAngleTo180(targetYaw - baseYaw);
            double horizDist = Math.sqrt(horizDistSq);
            targetPitch = (float) (-(Math.atan2(deltaY, horizDist) * 57.2957795));
        }

        float pitch = basePitch + wrapAngleTo180(targetPitch - basePitch);
        return new float[] { yaw, clampPitch(pitch) };
    }

    static double[] getAimPoint(Entity entity, double horizontalMultipoint, double verticalMultipoint) {
        EntityPlayerSP player = Mc.player();
        AxisAlignedBB bb = expandedBox(entity);
        if (player == null || bb == null)
            return null;

        double centerX = (bb.minX + bb.maxX) * 0.5;
        double centerY = entity.posY + eyeHeight(entity);
        double centerZ = (bb.minZ + bb.maxZ) * 0.5;

        double[] eye = eyePos(player, 1.0f);
        if (eye == null)
            return null;

        if (isVecInside(bb, eye[0], eye[1], eye[2]))
            return new double[] { centerX, eye[1], centerZ };

        double[] cl = closestPointOnAabb(eye[0], eye[1], eye[2], bb);
        double tH = Math.max(0.0, Math.min(1.0, horizontalMultipoint / 100.0));
        double tV = Math.max(0.0, Math.min(1.0, verticalMultipoint / 100.0));
        return new double[] {
                centerX + (cl[0] - centerX) * tH,
                centerY + (cl[1] - centerY) * tV,
                centerZ + (cl[2] - centerZ) * tH
        };
    }

    static boolean hasValidAimPoint(Entity entity, double hMult, double vMult, double range,
            boolean allowThroughBlocks, boolean allowThroughEntities) {
        double[] main = getAimPoint(entity, hMult, vMult);
        if (main == null)
            return false;
        double[] eye = eyePos(Mc.player(), 1.0f);
        if (eye == null)
            return false;
        double dx = main[0] - eye[0];
        double dy = main[1] - eye[1];
        double dz = main[2] - eye[2];
        if (dx * dx + dy * dy + dz * dz < 1.0E-12)
            return true;
        return canAimAtPoint(eye, main, entity, range, allowThroughBlocks, allowThroughEntities);
    }

    static float[] getRotationsWithBackup(Entity entity, double horizontalMultipoint, double verticalMultipoint,
            float baseYaw, float basePitch, double range,
            boolean allowThroughBlocks, boolean allowThroughEntities) {
        double[] main = getAimPoint(entity, horizontalMultipoint, verticalMultipoint);
        if (main == null)
            return null;
        double[] eye = eyePos(Mc.player(), 1.0f);
        AxisAlignedBB bb = expandedBox(entity);
        if (eye == null || bb == null)
            return null;

        if (isVecInside(bb, eye[0], eye[1], eye[2])) {
            double centerX = (bb.minX + bb.maxX) * 0.5;
            double centerZ = (bb.minZ + bb.maxZ) * 0.5;
            return getRotationsToPoint(centerX, eye[1], centerZ, baseYaw, basePitch);
        }

        if (canAimAtPoint(eye, main, entity, range, allowThroughBlocks, allowThroughEntities))
            return getRotationsToPoint(main[0], main[1], main[2], baseYaw, basePitch);

        return getRotations(entity, horizontalMultipoint, verticalMultipoint, baseYaw, basePitch);
    }

    private static boolean canAimAtPoint(double[] eye, double[] point, Entity target, double range,
            boolean allowThroughBlocks, boolean allowThroughEntities) {
        double dx = point[0] - eye[0];
        double dy = point[1] - eye[1];
        double dz = point[2] - eye[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6)
            return false;

        if (!allowThroughBlocks && blockBetween(eye, point))
            return false;
        return true;
    }

    private static boolean blockBetween(double[] eye, double[] point) {
        World world = Mc.world();
        if (world == null)
            return false;
        Vec3 start = new Vec3(eye[0], eye[1], eye[2]);
        Vec3 end = new Vec3(point[0], point[1], point[2]);
        MovingObjectPosition hit = world.rayTraceBlocks(start, end);
        return hit != null;
    }

    private static AxisAlignedBB expandedBox(Entity entity) {
        if (entity == null)
            return null;
        AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box == null)
            return null;
        float border = entity.getCollisionBorderSize();
        if (border <= 0.0f)
            return box;
        return box.expand(border, border, border);
    }

    private static double[] eyePos(Entity player, float partialTicks) {
        if (player == null)
            return null;
        if (partialTicks == 1.0f && player == eyeCachePlayer && eyeCache != null)
            return eyeCache;
        return eyePosUncached(player, partialTicks);
    }

    private static double[] eyePosUncached(Entity player, float partialTicks) {
        Vec3 vec = player.getPositionEyes(partialTicks);
        if (vec == null)
            return null;
        return new double[] { vec.xCoord, vec.yCoord, vec.zCoord };
    }

    private static float eyeHeight(Entity entity) {
        return entity != null ? entity.getEyeHeight() : 1.62f;
    }

    private static double[] closestPointOnAabb(double x, double y, double z, AxisAlignedBB aabb) {
        return new double[] {
                clamp(x, aabb.minX, aabb.maxX),
                clamp(y, aabb.minY, aabb.maxY),
                clamp(z, aabb.minZ, aabb.maxZ)
        };
    }

    private static boolean isVecInside(AxisAlignedBB aabb, double x, double y, double z) {
        return aabb != null && aabb.isVecInside(new Vec3(x, y, z));
    }

    private static double clamp(double v, double lo, double hi) {
        if (v > hi)
            return hi;
        if (v < lo)
            return lo;
        return v;
    }
}
