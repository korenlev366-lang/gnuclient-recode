package gnu.client.module.modules.player.scaffold.technique;

import gnu.client.module.modules.player.scaffold.PlacementTarget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;

public interface ScaffoldTechnique {
  PlacementTarget findTarget(EntityPlayer player, BlockPos intendedPlace, float baseYaw, float basePitch,
                             TechniqueContext ctx);

  final class TechniqueContext {
    public final int aimMode;
    public final int expandLength;
    public final boolean down;
    public final boolean tower;
    /** When true, never searchToward side fills (tower / telly). */
    public final boolean underfootOnly;

    public TechniqueContext(int aimMode, int expandLength, boolean down, boolean tower) {
      this(aimMode, expandLength, down, tower, tower);
    }

    public TechniqueContext(int aimMode, int expandLength, boolean down, boolean tower,
                            boolean underfootOnly) {
      this.aimMode = aimMode;
      this.expandLength = expandLength;
      this.down = down;
      this.tower = tower;
      this.underfootOnly = underfootOnly;
    }
  }
}
