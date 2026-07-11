package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.util.Comparator;

/**
 * Block-face factories and offset-priority comparators (LB {@code getFace} / {@code leastBlockDistance*}).
 */
public final class ScaffoldGeometry {
  private ScaffoldGeometry() {}

  /**
   * Build an {@link AlignedFace} in <b>block-local</b> coordinates (0..1 on the cube face).
   */
  public static AlignedFace fromBlockFace(BlockPos pos, EnumFacing face) {
    if (face == null || pos == null)
      return null;
    switch (face) {
      case DOWN:
        return new AlignedFace(new Vec3(0, 0, 0), new Vec3(1, 0, 1));
      case UP:
        return new AlignedFace(new Vec3(0, 1, 0), new Vec3(1, 1, 1));
      case NORTH:
        return new AlignedFace(new Vec3(0, 0, 0), new Vec3(1, 1, 0));
      case SOUTH:
        return new AlignedFace(new Vec3(0, 0, 1), new Vec3(1, 1, 1));
      case WEST:
        return new AlignedFace(new Vec3(0, 0, 0), new Vec3(0, 1, 1));
      case EAST:
        return new AlignedFace(new Vec3(1, 0, 0), new Vec3(1, 1, 1));
      default:
        return null;
    }
  }

  /** Squared distance from block center to line (full-cube approximation). */
  public static double blockDistanceSqToLine(BlockPos blockPos, Line3 line) {
    if (blockPos == null || line == null)
      return Double.POSITIVE_INFINITY;
    Vec3 center = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
    return line.distanceToSqr(center);
  }

  /** Squared distance from block center to a reference position. */
  public static double blockDistanceSqToPos(BlockPos blockPos, Vec3 pos) {
    if (blockPos == null || pos == null)
      return Double.POSITIVE_INFINITY;
    double dx = blockPos.getX() + 0.5 - pos.xCoord;
    double dy = blockPos.getY() + 0.5 - pos.yCoord;
    double dz = blockPos.getZ() + 0.5 - pos.zCoord;
    return dx * dx + dy * dy + dz * dz;
  }

  /** Closer to line first (LB descending priority via negated distance). */
  public static Comparator<BlockPos> leastBlockDistanceToLine(Line3 line) {
    return (a, b) -> Double.compare(blockDistanceSqToLine(a, line), blockDistanceSqToLine(b, line));
  }

  /** Closer to pos first. */
  public static Comparator<BlockPos> leastBlockDistanceToPos(Vec3 pos) {
    return (a, b) -> Double.compare(blockDistanceSqToPos(a, pos), blockDistanceSqToPos(b, pos));
  }
}
