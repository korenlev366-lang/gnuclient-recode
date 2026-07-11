package gnu.client.module.modules.player.scaffold.aim;

import gnu.client.runtime.RotationState;
import gnu.client.utility.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

/** LB {@code NearestRotationTargetPositionFactory}. */
public final class NearestRotationTargetPositionFactory extends FaceTargetPositionFactory {
  private final PositionFactoryConfiguration config;

  public NearestRotationTargetPositionFactory(PositionFactoryConfiguration config) {
    this.config = config;
  }

  @Override
  public Vec3 producePositionOnFace(AlignedFace face, BlockPos targetPos) {
    return aimAtNearestPointToRotationLine(targetPos, trimFace(face));
  }

  /**
   * Aim at the face point nearest the current look ray (LB uses
   * {@code RotationManager.serverRotation}).
   */
  public Vec3 aimAtNearestPointToRotationLine(BlockPos targetPos, AlignedFace face) {
    if (Math.abs(face.area()) < 1.0e-9)
      return face.from;

    float[] look = currentAimYawPitch();
    Vec3 eyeRel = new Vec3(
        config.eyePos.xCoord - targetPos.getX(),
        config.eyePos.yCoord - targetPos.getY(),
        config.eyePos.zCoord - targetPos.getZ());
    Line3 rotationLine = new Line3(eyeRel, Line3.directionVector(look[0], look[1]));
    return face.nearestPointTo(rotationLine);
  }

  /**
   * Prefer silent/server look so the sample matches what the server sees
   * (LB {@code RotationManager.serverRotation}).
   */
  static float[] currentAimYawPitch() {
    if (RotationState.isActived()) {
      return new float[] {
          RotationState.getRotationYawHead(),
          RotationState.getRotationPitch()
      };
    }
    float[] server = RotationUtils.serverRotations;
    if (server != null && server.length >= 2) {
      return new float[] { server[0], server[1] };
    }
    EntityPlayer player = Minecraft.getMinecraft().thePlayer;
    if (player != null)
      return new float[] { player.rotationYaw, player.rotationPitch };
    return new float[] { 0.0f, 0.0f };
  }
}
