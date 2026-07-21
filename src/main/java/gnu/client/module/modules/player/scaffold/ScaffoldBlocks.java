package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.mc.Mc;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.BlockWeb;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public final class ScaffoldBlocks {
    private ScaffoldBlocks() {}

    public static boolean isValidBlock(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock) || stack.stackSize <= 0)
            return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        if (block == null || block instanceof BlockFalling)
            return false;
        if (block == Blocks.tnt || block instanceof BlockTNT)
            return false;
        if (block == Blocks.web || block instanceof BlockWeb)
            return false;
        if (block == Blocks.portal)
            return false;
        return true;
    }

    /** Testable: first max count among valid; ties → lowest index; none → -1. */
    public static int pickBestSlot(int[] counts, boolean[] valid) {
        int best = -1;
        int bestCount = -1;
        int n = Math.min(counts.length, valid.length);
        for (int i = 0; i < n; i++) {
            if (!valid[i])
                continue;
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                best = i;
            }
        }
        return best;
    }

    public static int pickBestHotbarSlot(EntityPlayerSP player) {
        if (player == null)
            return -1;
        int[] counts = new int[9];
        boolean[] valid = new boolean[9];
        for (int i = 0; i < 9; i++) {
            ItemStack s = Mc.getStackInSlot(player.inventory, i);
            valid[i] = isValidBlock(s);
            counts[i] = valid[i] ? s.stackSize : 0;
        }
        return pickBestSlot(counts, valid);
    }
}
