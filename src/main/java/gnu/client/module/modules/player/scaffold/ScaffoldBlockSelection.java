package gnu.client.module.modules.player.scaffold;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Comparator;

/**
 * LiquidBounce {@code ScaffoldBlockItemSelection} + {@code BLOCK_COMPARATOR_FOR_HOTBAR}
 * PreferFavourable / Solid / FullCube / Walkable / Hardness / StackSize for 1.8.9.
 */
public final class ScaffoldBlockSelection {
  private static final double GOOD_HARDNESS_LO = 0.8;
  private static final double GOOD_HARDNESS_HI = 2.0;
  private static final double IDEAL_HARDNESS = 1.7;

  private ScaffoldBlockSelection() {}

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

  /**
   * LB hotbar chain: PreferFavourable → Solid → FullCube → Walkable →
   * PreferAverageHard(neutral) → PreferMore stack → PreferAverageHard(strict).
   * Higher compare result = better (maxWith).
   */
  public static final Comparator<ItemStack> HOTBAR_COMPARATOR = (o1, o2) -> {
    int c = Boolean.compare(!isUnfavourable(blockOf(o1)), !isUnfavourable(blockOf(o2)));
    if (c != 0) return c;
    c = Boolean.compare(isSolid(o1), isSolid(o2));
    if (c != 0) return c;
    c = Boolean.compare(isFullCube(o1), isFullCube(o2));
    if (c != 0) return c;
    c = compareWalkable(o1, o2);
    if (c != 0) return c;
    c = Double.compare(hardnessDist(o2, true), hardnessDist(o1, true));
    if (c != 0) return c;
    c = Integer.compare(countOf(o1), countOf(o2));
    if (c != 0) return c;
    return Double.compare(hardnessDist(o2, false), hardnessDist(o1, false));
  };

  /** Higher is better — delegates to {@link #HOTBAR_COMPARATOR} vs empty. */
  public static int score(ItemStack stack) {
    if (!isValidBlock(stack))
      return Integer.MIN_VALUE;
    // Rank among a fixed baseline so int score still works for simple loops.
    ItemStack baseline = new ItemStack(Blocks.dirt);
    int cmp = HOTBAR_COMPARATOR.compare(stack, baseline);
    int count = stack.stackSize;
    int base = isUnfavourable(blockOf(stack)) ? 0 : 1000;
    return base + count * 10 + cmp * 100 + hardnessScore(stack);
  }

  public static boolean isUnfavourable(Block block) {
    if (block == null)
      return true;
    if (block.slipperiness > 0.6f)
      return true;
    if (block instanceof ITileEntityProvider)
      return true;
    if (block instanceof BlockSlab || block instanceof BlockStairs)
      return true;
    if (block == Blocks.crafting_table || block == Blocks.enchanting_table
        || block == Blocks.cauldron || block == Blocks.soul_sand || block == Blocks.slime_block)
      return true;
    return !block.isFullBlock();
  }

  private static Block blockOf(ItemStack stack) {
    if (stack == null || !(stack.getItem() instanceof ItemBlock))
      return null;
    return ((ItemBlock) stack.getItem()).getBlock();
  }

  private static int countOf(ItemStack stack) {
    return stack == null ? 0 : stack.stackSize;
  }

  private static boolean isSolid(ItemStack stack) {
    Block b = blockOf(stack);
    return b != null && b.getMaterial() != null && b.getMaterial().isSolid();
  }

  private static boolean isFullCube(ItemStack stack) {
    Block b = blockOf(stack);
    if (b == null)
      return false;
    return b.isFullBlock() && b.isFullCube();
  }

  private static int compareWalkable(ItemStack a, ItemStack b) {
    Block ba = blockOf(a);
    Block bb = blockOf(b);
    if (ba == null && bb == null)
      return 0;
    if (ba == null)
      return -1;
    if (bb == null)
      return 1;
    int c = Float.compare(ba.slipperiness, bb.slipperiness);
    if (c != 0)
      return -c; // less slipperiness better → reverse
    // 1.8 has no jumpFactor/speedFactor — use slipperiness only + prefer full solid.
    return 0;
  }

  private static double hardnessDist(ItemStack stack, boolean neutralRange) {
    Block b = blockOf(stack);
    if (b == null)
      return Double.MAX_VALUE;
    World world = Minecraft.getMinecraft().theWorld;
    float hardness;
    try {
      hardness = b.getBlockHardness(world, null);
      if (hardness < 0)
        hardness = 100.0f;
    } catch (Throwable t) {
      hardness = 1.0f;
    }
    if (neutralRange && hardness >= GOOD_HARDNESS_LO && hardness <= GOOD_HARDNESS_HI)
      return 0.0;
    return Math.abs(IDEAL_HARDNESS - hardness);
  }

  private static int hardnessScore(ItemStack stack) {
    double d = hardnessDist(stack, true);
    return (int) Math.max(0, 50 - d * 20);
  }
}
