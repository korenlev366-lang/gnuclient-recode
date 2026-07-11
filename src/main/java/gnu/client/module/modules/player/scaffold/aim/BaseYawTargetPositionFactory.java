package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** LB {@code BaseYawTargetPositionFactory}. */
public abstract class BaseYawTargetPositionFactory extends FaceTargetPositionFactory {
  protected final PositionFactoryConfiguration config;
  private final float yawTolerance;

  protected BaseYawTargetPositionFactory(PositionFactoryConfiguration config) {
    this(config, 5.0f);
  }

  protected BaseYawTargetPositionFactory(PositionFactoryConfiguration config, float yawTolerance) {
    this.config = config;
    this.yawTolerance = yawTolerance;
  }

  protected abstract float getAngle();

  @Override
  public Vec3 producePositionOnFace(AlignedFace face, BlockPos targetPos) {
    EntityPlayer player = Minecraft.getMinecraft().thePlayer;
    AlignedFace trimmed = trimFace(face);
    if (player == null || !isMovingHorizontally(player))
      return aimAtNearestPointToRotationLine(targetPos, trimmed);
    Vec3 yawPoint = aimAtNearestPointToYaw(targetPos, trimmed, player);
    if (yawPoint != null)
      return yawPoint;
    return aimAtNearestPointToRotationLine(targetPos, trimmed);
  }

  protected Vec3 aimAtNearestPointToRotationLine(BlockPos targetPos, AlignedFace face) {
    return new NearestRotationTargetPositionFactory(config)
        .aimAtNearestPointToRotationLine(targetPos, face);
  }

  protected Vec3 aimAtNearestPointToYaw(BlockPos targetPos, AlignedFace face, EntityPlayer player) {
    if (Math.abs(face.area()) < 1.0e-9)
      return face.from;

    // Silent MoveFix: sample server/silent look, not camera yaw (same as NearestRotation).
    float yaw = MathHelper.wrapAngleTo180_float(
        NearestRotationTargetPositionFactory.currentAimYawPitch()[0]);
    float angle = getAngle();
    float highTargetYaw = MathHelper.wrapAngleTo180_float(yaw + angle);
    float lowTargetYaw = MathHelper.wrapAngleTo180_float(yaw - angle);

    Vec3 eyeRel = new Vec3(
        config.eyePos.xCoord - targetPos.getX(),
        config.eyePos.yCoord - targetPos.getY(),
        config.eyePos.zCoord - targetPos.getZ());

    NormalizedPlane highPlane = NormalizedPlane.fromParams(
        eyeRel, Line3.zAxisYRot(highTargetYaw), new Vec3(0, 1, 0));
    NormalizedPlane lowPlane = NormalizedPlane.fromParams(
        eyeRel, Line3.zAxisYRot(lowTargetYaw), new Vec3(0, 1, 0));

    Line3 highIntersectLine = null;
    Line3 lowIntersectLine = null;
    try {
      highIntersectLine = face.toPlane().intersection(highPlane);
    } catch (RuntimeException ignored) {
    }
    try {
      lowIntersectLine = face.toPlane().intersection(lowPlane);
    } catch (RuntimeException ignored) {
    }

    LineSegment3 highSeg = null;
    LineSegment3 lowSeg = null;
    try {
      if (highIntersectLine != null)
        highSeg = face.coerceInFace(highIntersectLine);
    } catch (RuntimeException ignored) {
    }
    try {
      if (lowIntersectLine != null)
        lowSeg = face.coerceInFace(lowIntersectLine);
    } catch (RuntimeException ignored) {
    }

    if (highSeg == null && lowSeg == null)
      return null;

    Vec3 highClosest = highSeg != null ? findClosestPointToYaw(highSeg, highTargetYaw, eyeRel) : null;
    Vec3 lowClosest = lowSeg != null ? findClosestPointToYaw(lowSeg, lowTargetYaw, eyeRel) : null;

    float highTol = highClosest != null
        ? calculateYawDifference(highClosest, highTargetYaw, eyeRel) : Float.MAX_VALUE;
    float lowTol = lowClosest != null
        ? calculateYawDifference(lowClosest, lowTargetYaw, eyeRel) : Float.MAX_VALUE;

    if (highTol <= yawTolerance && lowTol <= yawTolerance)
      return highTol < lowTol ? highClosest : lowClosest;
    if (highTol <= yawTolerance)
      return highClosest;
    if (lowTol <= yawTolerance)
      return lowClosest;
    return null;
  }

  private Vec3 findClosestPointToYaw(LineSegment3 segment, float targetYaw, Vec3 eyeRel) {
    Vec3 start = segment.start;
    Vec3 end = segment.end;
    Vec3 delta = Line3.sub(end, start);
    float startYaw = calculateYaw(start, eyeRel);
    float endYaw = calculateYaw(end, eyeRel);
    float yawDiff = MathHelper.wrapAngleTo180_float(endYaw - startYaw);
    float targetYawDiff = MathHelper.wrapAngleTo180_float(targetYaw - startYaw);
    double t = yawDiff != 0.0f ? targetYawDiff / yawDiff : 0.0;
    t = Line3.clamp01(t);
    return Line3.add(start, Line3.scale(delta, t));
  }

  private static float calculateYaw(Vec3 pointRel, Vec3 eyeRel) {
    return Line3.yawOf(Line3.sub(pointRel, eyeRel));
  }

  private static float calculateYawDifference(Vec3 pointRel, float targetYaw, Vec3 eyeRel) {
    return Math.abs(MathHelper.wrapAngleTo180_float(calculateYaw(pointRel, eyeRel) - targetYaw));
  }

  static boolean isMovingHorizontally(EntityPlayer player) {
    return Math.abs(player.moveForward) > 1.0e-3 || Math.abs(player.moveStrafing) > 1.0e-3;
  }
}
