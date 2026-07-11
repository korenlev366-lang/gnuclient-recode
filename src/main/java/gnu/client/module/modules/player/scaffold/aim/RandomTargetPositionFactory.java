package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

public final class RandomTargetPositionFactory extends FaceTargetPositionFactory {
  public static final RandomTargetPositionFactory INSTANCE = new RandomTargetPositionFactory();

  private RandomTargetPositionFactory() {}

  @Override
  public Vec3 producePositionOnFace(AlignedFace face, BlockPos targetPos) {
    return trimFace(face).randomPointOnFace();
  }
}
