package gnu.client.module.modules.player.scaffold;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public final class ScaffoldTarget {
    public final BlockPos support;
    public final EnumFacing face;
    public final BlockPos placed;

    public ScaffoldTarget(BlockPos support, EnumFacing face) {
        this.support = support;
        this.face = face;
        this.placed = support.offset(face);
    }
}
