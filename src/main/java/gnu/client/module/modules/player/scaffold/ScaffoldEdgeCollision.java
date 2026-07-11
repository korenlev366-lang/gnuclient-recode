package gnu.client.module.modules.player.scaffold;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * LiquidBounce {@code findEdgeCollision} for 1.8.9 — first point along a segment where
 * the player would leave supporting collision boxes.
 */
public final class ScaffoldEdgeCollision {
  private ScaffoldEdgeCollision() {}

  public static Vec3 findEdgeCollision(World world, Vec3 from, Vec3 to, float allowedDropDown) {
    if (world == null || from == null || to == null)
      return null;
    double ldx = to.xCoord - from.xCoord;
    double ldy = to.yCoord - from.yCoord;
    double ldz = to.zCoord - from.zCoord;
    double lenSq = ldx * ldx + ldy * ldy + ldz * ldz;
    if (lenSq <= 1.0e-12)
      return null;

    ArrayList<AxisAlignedBB> boxes = collectCollisionBoxes(world, from, to, allowedDropDown);
    Vec3 currentFrom = from;
    Vec3 extendedFrom = new Vec3(from.xCoord - 1000.0 * ldx, from.yCoord - 1000.0 * ldy,
        from.zCoord - 1000.0 * ldz);
    Vec3 extendedTo = new Vec3(to.xCoord + 1000.0 * ldx, to.yCoord + 1000.0 * ldy,
        to.zCoord + 1000.0 * ldz);

    for (int guard = 0; guard < 64; guard++) {
      ArrayList<AxisAlignedBB> containing = new ArrayList<>(4);
      for (AxisAlignedBB box : boxes) {
        if (box != null && containsInclusive(box, currentFrom))
          containing.add(box);
      }
      if (containing.isEmpty())
        return currentFrom;
      for (AxisAlignedBB box : containing) {
        if (containsInclusive(box, to))
          return null;
      }

      Vec3 best = null;
      double bestDist = Double.MAX_VALUE;
      for (AxisAlignedBB box : containing) {
        // LB clips extendedTo → extendedFrom to get the exit toward `to`.
        MovingObjectPosition mop = box.calculateIntercept(extendedTo, extendedFrom);
        if (mop == null || mop.hitVec == null)
          continue;
        double d = mop.hitVec.squareDistanceTo(to);
        if (d < bestDist) {
          bestDist = d;
          best = mop.hitVec;
        }
      }
      if (best == null)
        return currentFrom;
      currentFrom = best;
      boxes.removeAll(containing);
    }
    return null;
  }

  private static boolean containsInclusive(AxisAlignedBB box, Vec3 p) {
    return p.xCoord >= box.minX && p.xCoord <= box.maxX
        && p.yCoord >= box.minY && p.yCoord <= box.maxY
        && p.zCoord >= box.minZ && p.zCoord <= box.maxZ;
  }

  private static ArrayList<AxisAlignedBB> collectCollisionBoxes(World world, Vec3 from, Vec3 to,
                                                                float allowedDropDown) {
    // Player half-width 0.3 (width 0.6) — match LB expansion.
    double pad = 0.3;
    double minX = Math.min(from.xCoord, to.xCoord) - pad - 1.0e-7;
    double maxX = Math.max(from.xCoord, to.xCoord) + pad + 1.0e-7;
    double minY = Math.min(from.yCoord, to.yCoord) - allowedDropDown - 1.0e-7;
    double maxY = Math.min(from.yCoord, to.yCoord) + 1.0e-7;
    double minZ = Math.min(from.zCoord, to.zCoord) - pad - 1.0e-7;
    double maxZ = Math.max(from.zCoord, to.zCoord) + pad + 1.0e-7;

    int x0 = MathHelper.floor_double(minX);
    int x1 = MathHelper.floor_double(maxX);
    int y0 = MathHelper.floor_double(minY);
    int y1 = MathHelper.floor_double(maxY);
    int z0 = MathHelper.floor_double(minZ);
    int z1 = MathHelper.floor_double(maxZ);

    double ldx = to.xCoord - from.xCoord;
    double ldy = to.yCoord - from.yCoord;
    double ldz = to.zCoord - from.zCoord;
    Vec3 extendedFrom = new Vec3(from.xCoord - 1000.0 * ldx, from.yCoord - 1000.0 * ldy,
        from.zCoord - 1000.0 * ldz);
    Vec3 extendedTo = new Vec3(to.xCoord + 1000.0 * ldx, to.yCoord + 1000.0 * ldy,
        to.zCoord + 1000.0 * ldz);

    ArrayList<AxisAlignedBB> found = new ArrayList<>();
    for (int x = x0; x <= x1; x++) {
      for (int y = y0; y <= y1; y++) {
        for (int z = z0; z <= z1; z++) {
          BlockPos pos = new BlockPos(x, y, z);
          IBlockState state = world.getBlockState(pos);
          if (state == null)
            continue;
          Block block = state.getBlock();
          if (block == null)
            continue;
          List<AxisAlignedBB> parts = new ArrayList<>(2);
          AxisAlignedBB mask = new AxisAlignedBB(minX, minY, minZ, maxX, maxY + allowedDropDown + 0.05, maxZ);
          block.addCollisionBoxesToList(world, pos, state, mask, parts, null);
          for (AxisAlignedBB part : parts) {
            if (part == null)
              continue;
            AxisAlignedBB adjusted = new AxisAlignedBB(
                part.minX - pad, part.minY - 1.0, part.minZ - pad,
                part.maxX + pad, part.maxY + allowedDropDown + 0.05, part.maxZ + pad);
            MovingObjectPosition hit = adjusted.calculateIntercept(extendedFrom, extendedTo);
            if (hit != null)
              found.add(adjusted);
          }
        }
      }
    }
    return found;
  }
}
