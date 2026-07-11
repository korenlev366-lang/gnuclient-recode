package gnu.client.module.modules.player.scaffold.technique;

import gnu.client.module.modules.player.scaffold.PlacementTarget;
import gnu.client.module.modules.player.scaffold.ScaffoldTargetFinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;

public final class ExpandTechnique implements ScaffoldTechnique {
  public static final ExpandTechnique INSTANCE = new ExpandTechnique();

  private ExpandTechnique() {}

  @Override
  public PlacementTarget findTarget(EntityPlayer player, BlockPos intendedPlace, float baseYaw, float basePitch,
                                    TechniqueContext ctx) {
    // LB Expand: Center aim, search from predicted feet, ray-validate.
    PlacementTarget expand = ScaffoldTargetFinding.findExpandTarget(player, baseYaw,
        Math.max(1, ctx.expandLength), basePitch, ScaffoldTargetFinding.AIM_CENTER);
    if (expand != null)
      return expand;
    return ScaffoldTargetFinding.findTarget(player, intendedPlace, ScaffoldTargetFinding.OFFSETS_EXPAND,
        ScaffoldTargetFinding.AIM_CENTER, baseYaw, basePitch, ctx.expandLength, true);
  }
}
