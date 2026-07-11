package gnu.client.module.modules.player.scaffold.aim;

import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

public final class CenterTargetPositionFactory extends FaceTargetPositionFactory {
  public static final CenterTargetPositionFactory INSTANCE = new CenterTargetPositionFactory();

  private CenterTargetPositionFactory() {}

  @Override
  public Vec3 producePositionOnFace(AlignedFace face, BlockPos targetPos) {
    return face.center();
  }
}
