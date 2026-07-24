package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.utility.ItemUtil;
import gnu.client.utility.Utils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldSettings;

/**
 * wsamiaw ChestStealer — shift-clicks useful items out of chests.
 */
public final class ChestStealerModule extends Module {

    private final SliderSetting minDelay = addSetting(new SliderSetting("Min delay", 1f, 0f, 20f, 1f));
    private final SliderSetting maxDelay = addSetting(new SliderSetting("Max delay", 2f, 0f, 20f, 1f));
    private final SliderSetting openDelay = addSetting(new SliderSetting("Open delay", 1f, 0f, 20f, 1f));
    private final BoolSetting autoClose = addSetting(new BoolSetting("Auto close", false));
    private final BoolSetting nameCheck = addSetting(new BoolSetting("Name check", true));
    private final BoolSetting skipTrash = addSetting(new BoolSetting("Skip trash", true));
    private final BoolSetting moreArmor = addSetting(new BoolSetting("More armor", false));
    private final BoolSetting moreSword = addSetting(new BoolSetting("More sword", false));

    private int clickDelay;
    private int oDelay;
    private boolean inChest;
    private boolean warnedFull;

    public ChestStealerModule() {
        super("ChestStealer", "Steals items from opened chests", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        clickDelay = 0;
        oDelay = 0;
        inChest = false;
        warnedFull = false;
    }

    /**
     * PRE-style (before living update / {@code C03}): chest clicks after flying flag Grim
     * {@code Post} on 1.8 ({@code click window}).
     */
    @Override
    public void onTickStart() {
        if (clickDelay > 0)
            clickDelay--;
        if (oDelay > 0)
            oDelay--;

        EntityPlayerSP player = Mc.player();
        if (player == null) {
            inChest = false;
            return;
        }

        if (!(Mc.currentScreen() instanceof GuiChest)) {
            inChest = false;
            return;
        }

        GuiChest gui = (GuiChest) Mc.currentScreen();
        Container container = gui.inventorySlots;
        if (!(container instanceof ContainerChest)) {
            inChest = false;
            return;
        }

        if (!inChest) {
            inChest = true;
            warnedFull = false;
            oDelay = Math.round(openDelay.getValue()) + 1;
        }
        if (oDelay > 0 || clickDelay > 0)
            return;
        if (!isValidGameMode())
            return;

        ContainerChest chest = (ContainerChest) container;
        IInventory inventory = chest.getLowerChestInventory();
        if (nameCheck.getValue()) {
            String name = inventory.getName();
            if (!name.equals(I18n.format("container.chest"))
                    && !name.equals(I18n.format("container.chestDouble")))
                return;
        }

        if (player.inventory.getFirstEmptyStack() == -1) {
            if (!warnedFull) {
                player.addChatMessage(new ChatComponentText(
                        "§7[§fGNU§7] §cChestStealer: inventory is full!"));
                warnedFull = true;
            }
            if (autoClose.getValue())
                player.closeScreen();
            return;
        }

        int windowId = container.windowId;

        if (skipTrash.getValue()) {
            int bestSword = -1;
            double bestDamage = 0.0;
            int[] bestArmorSlots = new int[] {-1, -1, -1, -1};
            double[] bestArmorProtection = new double[] {0.0, 0.0, 0.0, 0.0};
            int bestPickaxeSlot = -1;
            float bestPickaxeEfficiency = 1.0f;
            int bestShovelSlot = -1;
            float bestShovelEfficiency = 1.0f;
            int bestAxeSlot = -1;
            float bestAxeEfficiency = 1.0f;
            int bestBow = -1;
            double bestBowDamage = 0.0;

            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                if (!container.getSlot(i).getHasStack())
                    continue;
                ItemStack stack = container.getSlot(i).getStack();
                Item item = stack.getItem();
                if (item instanceof ItemSword) {
                    double damage = ItemUtil.getAttackBonus(stack);
                    if (bestSword == -1 || damage > bestDamage) {
                        bestSword = i;
                        bestDamage = damage;
                    }
                } else if (item instanceof ItemArmor) {
                    int armorType = ((ItemArmor) item).armorType;
                    double protection = ItemUtil.getArmorProtection(stack);
                    if (bestArmorSlots[armorType] == -1 || protection > bestArmorProtection[armorType]) {
                        bestArmorSlots[armorType] = i;
                        bestArmorProtection[armorType] = protection;
                    }
                } else if (item instanceof ItemPickaxe) {
                    float efficiency = ItemUtil.getToolEfficiency(stack);
                    if (bestPickaxeSlot == -1 || efficiency > bestPickaxeEfficiency) {
                        bestPickaxeSlot = i;
                        bestPickaxeEfficiency = efficiency;
                    }
                } else if (item instanceof ItemSpade) {
                    float efficiency = ItemUtil.getToolEfficiency(stack);
                    if (bestShovelSlot == -1 || efficiency > bestShovelEfficiency) {
                        bestShovelSlot = i;
                        bestShovelEfficiency = efficiency;
                    }
                } else if (item instanceof ItemAxe) {
                    float efficiency = ItemUtil.getToolEfficiency(stack);
                    if (bestAxeSlot == -1 || efficiency > bestAxeEfficiency) {
                        bestAxeSlot = i;
                        bestAxeEfficiency = efficiency;
                    }
                } else if (item instanceof ItemBow) {
                    double damage = ItemUtil.getBowAttackBonus(stack);
                    if (bestBow == -1 || damage > bestBowDamage) {
                        bestBow = i;
                        bestBowDamage = damage;
                    }
                }
            }

            int swordInInv = ItemUtil.findSwordInInventorySlot(0, true);
            double invSwordDamage = swordInInv != -1
                    ? ItemUtil.getAttackBonus(player.inventory.getStackInSlot(swordInInv)) : 0.0;
            if (bestSword != -1 && bestDamage > invSwordDamage) {
                shiftClick(windowId, bestSword);
                return;
            }

            for (int i = 0; i < 4; i++) {
                int slot = ItemUtil.findArmorInventorySlot(i, true);
                double protection = slot != -1
                        ? ItemUtil.getArmorProtection(player.inventory.getStackInSlot(slot)) : 0.0;
                if (bestArmorSlots[i] != -1 && bestArmorProtection[i] > protection) {
                    shiftClick(windowId, bestArmorSlots[i]);
                    return;
                }
            }

            int pickaxeSlot = ItemUtil.findInventorySlot("pickaxe", 0, true);
            float pickaxeEfficiency = pickaxeSlot != -1
                    ? ItemUtil.getToolEfficiency(player.inventory.getStackInSlot(pickaxeSlot)) : 1.0f;
            if (bestPickaxeSlot != -1 && bestPickaxeEfficiency > pickaxeEfficiency) {
                shiftClick(windowId, bestPickaxeSlot);
                return;
            }

            int shovelSlot = ItemUtil.findInventorySlot("shovel", 0, true);
            float shovelEfficiency = shovelSlot != -1
                    ? ItemUtil.getToolEfficiency(player.inventory.getStackInSlot(shovelSlot)) : 1.0f;
            if (bestShovelSlot != -1 && bestShovelEfficiency > shovelEfficiency) {
                shiftClick(windowId, bestShovelSlot);
                return;
            }

            int axeSlot = ItemUtil.findInventorySlot("axe", 0, true);
            float axeEfficiency = axeSlot != -1
                    ? ItemUtil.getToolEfficiency(player.inventory.getStackInSlot(axeSlot)) : 1.0f;
            if (bestAxeSlot != -1 && bestAxeEfficiency > axeEfficiency) {
                shiftClick(windowId, bestAxeSlot);
                return;
            }

            int bowSlot = ItemUtil.findBowInventorySlot(0, true);
            double bowDamage = bowSlot != -1
                    ? ItemUtil.getBowAttackBonus(player.inventory.getStackInSlot(bowSlot)) : 0.0;
            if (bestBow != -1 && bestBowDamage > bowDamage) {
                shiftClick(windowId, bestBow);
                return;
            }
        }

        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (!container.getSlot(i).getHasStack())
                continue;
            ItemStack stack = container.getSlot(i).getStack();
            if (!skipTrash.getValue() || !ItemUtil.isNotSpecialItem(stack)
                    || isMoreArmor(stack) || isMoreSword(stack) || isInvManagerRequire(stack)) {
                shiftClick(windowId, i);
                return;
            }
        }

        if (autoClose.getValue())
            player.closeScreen();
    }

    private void shiftClick(int windowId, int slotId) {
        if (Mc.windowClick(windowId, slotId, 0, 1))
            armDelay();
    }

    private void armDelay() {
        int min = Math.round(minDelay.getValue());
        int max = Math.round(maxDelay.getValue());
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        clickDelay = Utils.randomizeInt(min + 1, max + 1);
    }

    private boolean isMoreArmor(ItemStack itemStack) {
        if (itemStack == null || !moreArmor.getValue())
            return false;
        if (!(itemStack.getItem() instanceof ItemArmor))
            return false;
        ItemArmor.ArmorMaterial material = ((ItemArmor) itemStack.getItem()).getArmorMaterial();
        if (material == ItemArmor.ArmorMaterial.DIAMOND)
            return true;
        return material == ItemArmor.ArmorMaterial.IRON && itemStack.isItemEnchanted();
    }

    private boolean isMoreSword(ItemStack itemStack) {
        if (itemStack == null || !moreSword.getValue())
            return false;
        if (!(itemStack.getItem() instanceof ItemSword))
            return false;
        double damage = ItemUtil.getAttackBonus(itemStack);
        if (EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack) != 0)
            return true;
        // Diamond base ~7; iron enchanted ~6+
        return damage >= 7.0 || (damage >= 6.0 && itemStack.isItemEnchanted());
    }

    private boolean isInvManagerRequire(ItemStack itemStack) {
        if (itemStack == null)
            return false;
        Module module = ModuleManager.instance().getModule("InvManager");
        InvManagerModule inv = module instanceof InvManagerModule ? (InvManagerModule) module : null;
        if (ItemUtil.ItemType.Block.contains(itemStack)) {
            return inv == null || !inv.isEnabled()
                    || ItemUtil.countInventory(ItemUtil.ItemType.Block) < inv.getBlocksKeep();
        }
        if (ItemUtil.ItemType.Projectile.contains(itemStack)) {
            return inv == null || !inv.isEnabled()
                    || ItemUtil.countInventory(ItemUtil.ItemType.Projectile) < inv.getProjectilesKeep();
        }
        if (ItemUtil.ItemType.FishRod.contains(itemStack))
            return ItemUtil.countInventory(ItemUtil.ItemType.Projectile) == 0;
        if (ItemUtil.ItemType.Arrow.contains(itemStack)) {
            return inv == null || !inv.isEnabled()
                    || ItemUtil.countInventory(ItemUtil.ItemType.Arrow) < inv.getArrowKeep();
        }
        return false;
    }

    private static boolean isValidGameMode() {
        if (Mc.controller() == null)
            return false;
        WorldSettings.GameType type = Mc.controller().getCurrentGameType();
        return type == WorldSettings.GameType.SURVIVAL || type == WorldSettings.GameType.ADVENTURE;
    }
}
