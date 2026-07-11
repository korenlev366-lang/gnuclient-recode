package gnu.client.module.modules.player.scaffold.technique;

import gnu.client.module.modules.player.scaffold.PlacementTarget;
import gnu.client.module.modules.player.scaffold.ScaffoldTargetFinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;

public final class NormalTechnique implements ScaffoldTechnique {
  public static final NormalTechnique INSTANCE = new NormalTechnique();

  private NormalTechnique() {}

  @Override
  public PlacementTarget findTarget(EntityPlayer player, BlockPos intendedPlace, float baseYaw, float basePitch,
                                    TechniqueContext ctx) {
    int offsets = ctx.down ? ScaffoldTargetFinding.OFFSETS_DOWN : ScaffoldTargetFinding.OFFSETS_NORMAL;
    // LB NormalTechnique: considerFacingAway only when going down. Tower: UP face only.
    return ScaffoldTargetFinding.findTarget(player, intendedPlace, offsets, ctx.aimMode,
        baseYaw, basePitch, 0, ctx.down, ctx.underfootOnly, ctx.tower);
  }
}
