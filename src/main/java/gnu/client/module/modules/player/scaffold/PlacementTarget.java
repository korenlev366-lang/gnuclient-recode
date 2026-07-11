package gnu.client.module.modules.player.scaffold;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

/**
 * Single placement plan — aim, raycast, and place all share this object
 * (LiquidBounce {@code BlockPlacementTarget} shape).
 */
public final class PlacementTarget {
  public final BlockPos interactedBlockPos;
  public final BlockPos placedBlockPos;
  public final int faceOrdinal;
  public final Vec3 hitVec;
  public final float yaw;
  public final float pitch;
  public final double minPlacementY;

  public PlacementTarget(BlockPos interactedBlockPos, BlockPos placedBlockPos, int faceOrdinal,
                         Vec3 hitVec, float yaw, float pitch, double minPlacementY) {
    this.interactedBlockPos = interactedBlockPos;
    this.placedBlockPos = placedBlockPos;
    this.faceOrdinal = faceOrdinal;
    this.hitVec = hitVec;
    this.yaw = yaw;
    this.pitch = pitch;
    this.minPlacementY = minPlacementY;
  }

  public EnumFacing facing() {
    return ScaffoldMath.enumFacing(faceOrdinal);
  }
}
