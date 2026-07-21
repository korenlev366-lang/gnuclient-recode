package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.mc.Mc;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Thin world / ray helpers for scaffold place targeting. */
public final class ScaffoldPlace {
    private static final EnumFacing[] BRIDGE_ORDER = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST,
            EnumFacing.DOWN, EnumFacing.UP
    };
    private static final EnumFacing[] TOWER_ORDER = {
            EnumFacing.UP,
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST,
            EnumFacing.DOWN
    };

    private ScaffoldPlace() {}

    public static boolean isReplaceable(World world, BlockPos pos) {
        if (world == null || pos == null)
            return false;
        Block block = world.getBlockState(pos).getBlock();
        if (!block.getMaterial().isReplaceable())
            return false;
        if (!(block instanceof BlockSnow))
            return true;
        return !(block.getBlockBoundsMaxY() > 0.125);
    }

    public static boolean isValidSupport(World world, BlockPos pos) {
        return pos != null && !isReplaceable(world, pos);
    }

    /** Bridge search: horizontals + down first; UP last. */
    public static ScaffoldTarget findNeighborTarget(EntityPlayerSP player, World world, BlockPos under) {
        return findNeighborTarget(player, world, under, false);
    }

    /**
     * Scan neighbors of {@code under} for a solid support whose {@code offset(face)} is the
     * replaceable cell. When {@code preferUp}, try {@link EnumFacing#UP} first (tower).
     */
    public static ScaffoldTarget findNeighborTarget(EntityPlayerSP player, World world,
            BlockPos under, boolean preferUp) {
        if (player == null || world == null || under == null)
            return null;
        if (!isReplaceable(world, under))
            return null;
        EnumFacing[] order = preferUp ? TOWER_ORDER : BRIDGE_ORDER;
        for (EnumFacing face : order) {
            BlockPos support = under.offset(face.getOpposite());
            if (!isValidSupport(world, support))
                continue;
            if (!isReplaceable(world, support.offset(face)))
                continue;
            return new ScaffoldTarget(support, face);
        }
        return null;
    }

    public static float[] rotationsTo(Vec3 point, EntityPlayer player, float baseYaw, float basePitch) {
        if (point == null || player == null)
            return new float[] { baseYaw, basePitch };
        Vec3 eye = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        double dx = point.xCoord - eye.xCoord;
        double dy = point.yCoord - eye.yCoord;
        double dz = point.zCoord - eye.zCoord;
        double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI));
        yaw = baseYaw + ScaffoldRotations.wrap(yaw - baseYaw);
        pitch = ScaffoldRotations.clampPitch(basePitch + ScaffoldRotations.wrap(pitch - basePitch));
        return new float[] { yaw, pitch };
    }

    public static Vec3 findPlacementHit(EntityPlayer player, BlockPos support, EnumFacing face,
            float yaw, float pitch) {
        if (player == null || support == null || face == null)
            return null;
        float reach = 4.5f;
        if (Mc.controller() != null)
            reach = Mc.controller().getBlockReachDistance();
        MovingObjectPosition mop = rayTrace(player, yaw, pitch, reach);
        if (mop == null
                || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mop.getBlockPos() == null
                || !mop.getBlockPos().equals(support)
                || mop.sideHit != face)
            return null;
        return mop.hitVec;
    }

    public static MovingObjectPosition rayTrace(EntityPlayer player, float yaw, float pitch, float reach) {
        if (player == null)
            return null;
        World world = player.worldObj;
        if (world == null)
            return null;
        Vec3 start = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosPitch = MathHelper.cos(pitchRad);
        float dx = -MathHelper.sin(yawRad) * cosPitch;
        float dy = -MathHelper.sin(pitchRad);
        float dz = MathHelper.cos(yawRad) * cosPitch;
        Vec3 end = start.addVector(dx * reach, dy * reach, dz * reach);
        return world.rayTraceBlocks(start, end, false, false, false);
    }

    public static Vec3 faceCenter(BlockPos support, EnumFacing face) {
        if (support == null || face == null)
            return null;
        double x = support.getX() + 0.5;
        double y = support.getY() + 0.5;
        double z = support.getZ() + 0.5;
        switch (face) {
            case DOWN:
                y = support.getY();
                break;
            case UP:
                y = support.getY() + 1.0;
                break;
            case NORTH:
                z = support.getZ();
                break;
            case SOUTH:
                z = support.getZ() + 1.0;
                break;
            case WEST:
                x = support.getX();
                break;
            case EAST:
                x = support.getX() + 1.0;
                break;
            default:
                break;
        }
        return new Vec3(x, y, z);
    }
}
