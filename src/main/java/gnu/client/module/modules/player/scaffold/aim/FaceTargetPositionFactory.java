package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

/**
 * LB {@code FaceTargetPositionFactory} — samples a hit point relative to {@code targetPos} origin.
 * Face is in block-local / origin-relative coords.
 */
public abstract class FaceTargetPositionFactory {

  /**
   * @param face relative to origin (typically block-local 0..1)
   * @param targetPos support block being clicked
   * @return point relative to {@code targetPos}, or null
   */
  public abstract Vec3 producePositionOnFace(AlignedFace face, BlockPos targetPos);

  /** Trim face to 15% inset (LB). */
  protected AlignedFace trimFace(AlignedFace face) {
    Vec3 dims = face.dimensions();
    double ox = dims.xCoord * 0.15;
    double oy = dims.yCoord * 0.15;
    double oz = dims.zCoord * 0.15;

    double minX = face.from.xCoord + ox;
    double maxX = face.to.xCoord - ox;
    double minY = face.from.yCoord + oy;
    double maxY = face.to.yCoord - oy;
    double minZ = face.from.zCoord + oz;
    double maxZ = face.to.zCoord - oz;

    if (minX > maxX) {
      minX = maxX = face.center().xCoord;
    }
    if (minY > maxY) {
      minY = maxY = face.center().yCoord;
    }
    if (minZ > maxZ) {
      minZ = maxZ = face.center().zCoord;
    }

    return new AlignedFace(
        new Vec3(
            clamp(face.from.xCoord, minX, maxX),
            clamp(face.from.yCoord, minY, maxY),
            clamp(face.from.zCoord, minZ, maxZ)),
        new Vec3(
            clamp(face.to.xCoord, minX, maxX),
            clamp(face.to.yCoord, minY, maxY),
            clamp(face.to.zCoord, minZ, maxZ)));
  }

  private static double clamp(double v, double lo, double hi) {
    if (v < lo)
      return lo;
    if (v > hi)
      return hi;
    return v;
  }
}
