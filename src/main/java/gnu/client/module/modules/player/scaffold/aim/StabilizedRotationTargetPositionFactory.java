package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

/** LB {@code StabilizedRotationTargetPositionFactory}. */
public final class StabilizedRotationTargetPositionFactory extends FaceTargetPositionFactory {
  private final PositionFactoryConfiguration config;
  private final Line3 optimalLine;

  public StabilizedRotationTargetPositionFactory(PositionFactoryConfiguration config, Line3 optimalLine) {
    this.config = config;
    this.optimalLine = optimalLine;
  }

  @Override
  public Vec3 producePositionOnFace(AlignedFace face, BlockPos targetPos) {
    EntityPlayer player = Minecraft.getMinecraft().thePlayer;
    AlignedFace trimmedWorld = trimFace(face).offset(targetPos.getX(), targetPos.getY(), targetPos.getZ());
    AlignedFace targetFace = getTargetFace(player, trimmedWorld);
    if (targetFace == null)
      targetFace = trimmedWorld;
    AlignedFace local = targetFace.offset(-targetPos.getX(), -targetPos.getY(), -targetPos.getZ());
    return new NearestRotationTargetPositionFactory(config)
        .aimAtNearestPointToRotationLine(targetPos, local);
  }

  private AlignedFace getTargetFace(EntityPlayer player, AlignedFace trimmedFace) {
    if (optimalLine == null || player == null)
      return null;

    Vec3 playerPos = new Vec3(player.posX, player.posY, player.posZ);
    Vec3 nearestOnLine = optimalLine.nearestPointTo(playerPos);
    Vec3 directionToOptimalLine = Line3.normalize(Line3.sub(playerPos, nearestOnLine));
    if (Line3.isLikelyZero(directionToOptimalLine))
      return null;

    Line3 optimalLineFromPlayer = new Line3(config.eyePos, optimalLine.direction);
    Vec3 collisionWithFacePlane = trimmedFace.toPlane().intersection(optimalLineFromPlayer);
    if (collisionWithFacePlane == null)
      return null;

    Vec3 b = Line3.add(playerPos, Line3.scale(directionToOptimalLine, 2.0));
    AxisAlignedBB cropBox = new AxisAlignedBB(
        Math.min(collisionWithFacePlane.xCoord, b.xCoord),
        player.posY - 2.0,
        Math.min(collisionWithFacePlane.zCoord, b.zCoord),
        Math.max(collisionWithFacePlane.xCoord, b.xCoord),
        player.posY + 1.0,
        Math.max(collisionWithFacePlane.zCoord, b.zCoord));

    AlignedFace clamped = trimmedFace.clamp(cropBox);
    if (clamped.area() < 0.0001)
      return null;
    return clamped;
  }
}
