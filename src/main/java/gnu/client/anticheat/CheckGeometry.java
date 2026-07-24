package gnu.client.anticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Pure helpers for combat geometry — shared by Reach / KillAura / MultiAura. */
public final class CheckGeometry {
    private CheckGeometry() {}

    public static Vec3 eyes(EntityPlayer player) {
        return new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    }

    public static AxisAlignedBB combatBox(EntityPlayer target) {
        return target.getEntityBoundingBox().expand(
                CheckRules.HITBOX_EXPAND_XZ, CheckRules.HITBOX_EXPAND_Y, CheckRules.HITBOX_EXPAND_XZ);
    }

    public static double distanceToBox(Vec3 point, AxisAlignedBB box) {
        double x = clamp(point.xCoord, box.minX, box.maxX);
        double y = clamp(point.yCoord, box.minY, box.maxY);
        double z = clamp(point.zCoord, box.minZ, box.maxZ);
        double dx = point.xCoord - x;
        double dy = point.yCoord - y;
        double dz = point.zCoord - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float yawTo(EntityPlayer from, EntityPlayer to) {
        double dx = to.posX - from.posX;
        double dz = to.posZ - from.posZ;
        return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
    }

    public static float pitchTo(EntityPlayer from, EntityPlayer to) {
        Vec3 a = eyes(from);
        Vec3 b = new Vec3(to.posX, to.posY + to.getEyeHeight() * 0.9, to.posZ);
        double dx = b.xCoord - a.xCoord;
        double dy = b.yCoord - a.yCoord;
        double dz = b.zCoord - a.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) -(Math.atan2(dy, horizontal) * 180.0 / Math.PI);
    }

    public static boolean isAimingAt(PlayerCheckData data, EntityPlayer from, EntityPlayer to,
                                     float maxYaw, float maxPitch) {
        if (data == null || from == null || to == null)
            return false;
        float yawErr = Math.abs(MathHelper.wrapAngleTo180_float(yawTo(from, to) - data.yaw));
        float pitchErr = Math.abs(pitchTo(from, to) - data.pitch);
        return yawErr <= maxYaw && pitchErr <= maxPitch;
    }

    /** Block along look ray closer than target → mining/building, not PvP. */
    public static boolean isLookingAtBlockCloserThan(EntityPlayer player, World world, EntityPlayer target) {
        if (player == null || world == null || target == null)
            return false;
        Vec3 start = eyes(player);
        float yaw = player.rotationYawHead;
        float pitch = player.rotationPitch;
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        Vec3 look = new Vec3(f1 * f2, f3, f * f2);
        double targetDist = distanceToBox(start, combatBox(target));
        double reach = Math.min(CheckRules.MAX_COMBAT_RANGE, targetDist + 0.05);
        Vec3 end = start.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition mop = world.rayTraceBlocks(start, end, false, true, false);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return false;
        return start.distanceTo(mop.hitVec) + 0.05 < targetDist;
    }
}
