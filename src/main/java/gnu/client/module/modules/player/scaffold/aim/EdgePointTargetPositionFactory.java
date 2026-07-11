package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

/** LB {@code EdgePointTargetPositionFactory}. */
public final class EdgePointTargetPositionFactory extends FaceTargetPositionFactory {
  private final PositionFactoryConfiguration config;

  public EdgePointTargetPositionFactory(PositionFactoryConfiguration config) {
    this.config = config;
  }

  @Override
  public Vec3 producePositionOnFace(AlignedFace face, BlockPos targetPos) {
    EntityPlayer player = Minecraft.getMinecraft().thePlayer;
    AlignedFace trimmed = trimFace(face);
    if (player == null || !BaseYawTargetPositionFactory.isMovingHorizontally(player))
      return aimAtNearest(targetPos, trimmed);
    Vec3 edge = aimAtFurthestPointToPlayerPosition(targetPos, trimmed, player);
    if (edge != null)
      return edge;
    return aimAtNearest(targetPos, trimmed);
  }

  private Vec3 aimAtNearest(BlockPos targetPos, AlignedFace face) {
    return new NearestRotationTargetPositionFactory(config)
        .aimAtNearestPointToRotationLine(targetPos, face);
  }

  private Vec3 aimAtFurthestPointToPlayerPosition(BlockPos targetPos, AlignedFace face,
                                                  EntityPlayer player) {
    // Silent MoveFix: reference aim-origin (predicted eye) + server look, not camera feet alone.
    float[] look = NearestRotationTargetPositionFactory.currentAimYawPitch();
    double yawRad = Math.toRadians(look[0]);
    // Slight offset opposite look so furthest-edge picks match server-facing side.
    double ox = Math.sin(yawRad) * 0.01;
    double oz = -Math.cos(yawRad) * 0.01;
    Vec3 playerRel = new Vec3(
        config.eyePos.xCoord - targetPos.getX() + ox,
        player.posY - targetPos.getY(),
        config.eyePos.zCoord - targetPos.getZ() + oz);
    Vec3 best = null;
    double bestDist = -1.0;
    double[] xs = {face.from.xCoord, face.to.xCoord};
    double[] ys = {face.from.yCoord, face.to.yCoord};
    double[] zs = {face.from.zCoord, face.to.zCoord};
    for (double x : xs) {
      for (double y : ys) {
        for (double z : zs) {
          Vec3 v = new Vec3(x, y, z);
          double d = Line3.distSq(v, playerRel);
          if (d > bestDist) {
            bestDist = d;
            best = v;
          }
        }
      }
    }
    return best;
  }
}
