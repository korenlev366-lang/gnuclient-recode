package gnu.client.utility;

import com.google.common.collect.Multimap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBasePressurePlate;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockEndPortal;
import net.minecraft.block.BlockEndPortalFrame;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockSlime;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.BlockTripWireHook;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.block.BlockWeb;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Inventory / item scoring helpers (wsamiaw {@code ItemUtil} port for InvManager + ChestStealer).
 */
public final class ItemUtil implements IMinecraftInstance {

    private static final ArrayList<Integer> SPECIAL_POTIONS = new SpecialPotions();

    private ItemUtil() {
    }

    public static boolean isNotSpecialItem(ItemStack itemStack) {
        if (itemStack == null)
            return false;
        Item item = itemStack.getItem();
        if (item instanceof ItemPotion) {
            java.util.List<PotionEffect> effects = ((ItemPotion) item).getEffects(itemStack);
            if (effects == null || effects.isEmpty())
                return true;
            for (PotionEffect effect : effects) {
                if (SPECIAL_POTIONS.contains(effect.getPotionID()))
                    return false;
            }
            return true;
        }
        if (item instanceof ItemEnderPearl)
            return false;
        if (item instanceof ItemFood)
            return item == Items.spider_eye;
        if (item instanceof ItemMonsterPlacer)
            return false;
        return item != Items.nether_star;
    }

    public static boolean isBlock(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1)
            return false;
        Item item = itemStack.getItem();
        if (!(item instanceof ItemBlock))
            return false;
        return isContainerBlock((ItemBlock) item);
    }

    public static boolean isProjectile(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1)
            return false;
        Item item = itemStack.getItem();
        return item instanceof ItemEgg || item instanceof ItemSnowball;
    }

    public static boolean isContainerBlock(ItemBlock itemBlock) {
        Block block = itemBlock.getBlock();
        if (BlockUtils.isInteractable(block))
            return false;
        return isScaffoldSolid(block);
    }

    /** wsamiaw BlockUtil.isSolid — placeable scaffold-style blocks only. */
    public static boolean isScaffoldSolid(Block block) {
        if (block instanceof BlockStairs || block instanceof BlockSlab
                || block instanceof BlockEndPortalFrame || block instanceof BlockEndPortal
                || block instanceof BlockVine || block instanceof BlockPumpkin
                || block instanceof BlockCactus || block instanceof BlockBush
                || block instanceof BlockFalling || block instanceof BlockWeb
                || block instanceof BlockPane || block instanceof BlockCarpet
                || block instanceof BlockSnow || block instanceof BlockFence
                || block instanceof BlockFenceGate || block instanceof BlockWall
                || block instanceof BlockLadder || block instanceof BlockTorch
                || block instanceof BlockRedstoneWire || block instanceof BlockRedstoneDiode
                || block instanceof BlockBasePressurePlate || block instanceof BlockTripWire
                || block instanceof BlockTripWireHook || block instanceof BlockRailBase
                || block instanceof BlockSlime || block instanceof BlockTNT)
            return false;
        return true;
    }

    public static double getAttackBonus(ItemStack itemStack) {
        double attackBonus = 0.0;
        if (itemStack == null)
            return 0.0;
        Multimap<String, AttributeModifier> multimap = itemStack.getAttributeModifiers();
        for (String attributeName : multimap.keySet()) {
            if (!"generic.attackDamage".equals(attributeName))
                continue;
            Iterator<AttributeModifier> iterator = multimap.get(attributeName).iterator();
            if (!iterator.hasNext())
                break;
            attackBonus += iterator.next().getAmount();
            break;
        }
        if (itemStack.isItemEnchanted()) {
            attackBonus += EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack);
            attackBonus += EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack) * 1.25;
        }
        return attackBonus;
    }

    public static float getToolEfficiency(ItemStack itemStack) {
        float efficiency = 1.0f;
        if (itemStack != null && itemStack.getItem() instanceof ItemTool) {
            efficiency = ((ItemTool) itemStack.getItem()).getToolMaterial().getEfficiencyOnProperMaterial();
            if (efficiency > 1.0f) {
                int enchantLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);
                if (enchantLevel > 0)
                    efficiency += (float) (enchantLevel * enchantLevel + 1);
            }
        }
        return efficiency;
    }

    public static double getArmorProtection(ItemStack itemStack) {
        double protection = 0.0;
        if (itemStack != null && itemStack.getItem() instanceof ItemArmor) {
            protection = ((ItemArmor) itemStack.getItem()).damageReduceAmount;
            if (itemStack.isItemEnchanted()) {
                protection += EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, itemStack) * 0.8;
                protection += EnchantmentHelper.getEnchantmentLevel(Enchantment.featherFalling.effectId, itemStack) * 0.05;
                protection += EnchantmentHelper.getEnchantmentLevel(
                        Enchantment.projectileProtection.effectId, itemStack) * 0.01;
            }
        }
        return protection;
    }

    public static double getBowAttackBonus(ItemStack itemStack) {
        double attackBonus = 0.0;
        if (itemStack != null && itemStack.getItem() instanceof ItemBow) {
            attackBonus = 2.0;
            if (itemStack.isItemEnchanted()) {
                int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, itemStack);
                if (power > 0)
                    attackBonus += (power + 1) * 0.25;
                attackBonus += EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, itemStack) * 0.25;
                attackBonus += EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, itemStack) * 0.05;
            }
        }
        return attackBonus;
    }

    public static int findSwordInInventorySlot(int startSlot, boolean checkDurability) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || startSlot < 0)
            return -1;
        int bestSlot = -1;
        double bestAttackBonus = 0.0;
        for (int i = 0; i < 36; i++) {
            int currentSlot = (startSlot + i) % 36;
            ItemStack itemStack = player.inventory.getStackInSlot(currentSlot);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemSword))
                continue;
            if (checkDurability && isTooDamaged(itemStack))
                continue;
            double attackBonus = getAttackBonus(itemStack);
            if (attackBonus > bestAttackBonus) {
                bestSlot = currentSlot;
                bestAttackBonus = attackBonus;
            }
        }
        return bestSlot;
    }

    public static int findBowInventorySlot(int startSlot, boolean checkDurability) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || startSlot < 0)
            return -1;
        int bestSlot = -1;
        double bestAttackBonus = 0.0;
        for (int i = 0; i < 36; i++) {
            int currentSlot = (startSlot + i) % 36;
            ItemStack itemStack = player.inventory.getStackInSlot(currentSlot);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemBow))
                continue;
            if (checkDurability && isTooDamaged(itemStack))
                continue;
            double attackBonus = getBowAttackBonus(itemStack);
            if (attackBonus > bestAttackBonus) {
                bestSlot = currentSlot;
                bestAttackBonus = attackBonus;
            }
        }
        return bestSlot;
    }

    public static int findInventorySlot(String toolClass, int startSlot, boolean checkDurability) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || startSlot < 0)
            return -1;
        int bestSlot = -1;
        float bestEfficiency = 1.0f;
        for (int i = 0; i < 36; i++) {
            int currentSlot = (startSlot + i) % 36;
            ItemStack itemStack = player.inventory.getStackInSlot(currentSlot);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemTool))
                continue;
            if (!itemStack.getItem().getToolClasses(itemStack).contains(toolClass))
                continue;
            if (checkDurability && isTooDamaged(itemStack))
                continue;
            float efficiency = getToolEfficiency(itemStack);
            if (efficiency > bestEfficiency) {
                bestSlot = currentSlot;
                bestEfficiency = efficiency;
            }
        }
        return bestSlot;
    }

    public static int findArmorInventorySlot(int armorType, boolean checkDurability) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null)
            return -1;
        int bestSlot = -1;
        double bestProtection = 0.0;
        for (int i = 0; i < 40; i++) {
            ItemStack itemStack = player.inventory.getStackInSlot(i);
            if (itemStack == null || !(itemStack.getItem() instanceof ItemArmor))
                continue;
            if (((ItemArmor) itemStack.getItem()).armorType != armorType)
                continue;
            if (checkDurability && isTooDamaged(itemStack))
                continue;
            double protection = getArmorProtection(itemStack);
            if (protection >= bestProtection) {
                bestSlot = i;
                bestProtection = protection;
            }
        }
        return bestSlot;
    }

    public static int findInventorySlot(int startSlot, ItemType itemType) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null)
            return -1;
        int bestSlot = -1;
        int maxStackSize = 0;
        if (startSlot < 0)
            startSlot = 0;
        for (int i = 0; i < 36; i++) {
            int currentSlot = (startSlot + i) % 36;
            ItemStack itemStack = player.inventory.getStackInSlot(currentSlot);
            if (itemStack == null || !itemType.contains(itemStack))
                continue;
            if (maxStackSize >= itemStack.stackSize)
                continue;
            bestSlot = currentSlot;
            maxStackSize = itemStack.stackSize;
        }
        return bestSlot;
    }

    /** Total stack count of {@code itemType} across main inventory. */
    public static int countInventory(ItemType itemType) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null)
            return 0;
        int stackSize = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = player.inventory.getStackInSlot(i);
            if (itemStack == null || !itemType.contains(itemStack))
                continue;
            stackSize += itemStack.stackSize;
        }
        return stackSize;
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemPickaxe;
    }

    public static boolean isShovel(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSpade;
    }

    public static boolean isAxe(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemAxe;
    }

    private static boolean isTooDamaged(ItemStack itemStack) {
        return itemStack.isItemDamaged()
                && itemStack.getMaxDamage() - itemStack.getItemDamage() < 30;
    }

    private static final class SpecialPotions extends ArrayList<Integer> {
        SpecialPotions() {
            add(1);
            add(3);
            add(5);
            add(6);
            add(8);
            add(10);
            add(11);
            add(12);
            add(14);
            add(21);
            add(22);
        }
    }

    public enum ItemType {
        Block {
            @Override
            public boolean contains(ItemStack itemStack) {
                return isBlock(itemStack);
            }
        },
        Projectile {
            @Override
            public boolean contains(ItemStack itemStack) {
                return isProjectile(itemStack);
            }
        },
        FishRod {
            @Override
            public boolean contains(ItemStack itemStack) {
                return itemStack != null && itemStack.getItem() instanceof ItemFishingRod;
            }
        },
        GoldApple {
            @Override
            public boolean contains(ItemStack itemStack) {
                return itemStack != null && itemStack.getItem() instanceof ItemAppleGold;
            }
        },
        Arrow {
            @Override
            public boolean contains(ItemStack itemStack) {
                return itemStack != null && itemStack.getItem() == Items.arrow;
            }
        };

        public abstract boolean contains(ItemStack itemStack);
    }
}
