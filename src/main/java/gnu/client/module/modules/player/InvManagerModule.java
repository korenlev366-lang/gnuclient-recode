package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.utility.ItemUtil;
import gnu.client.utility.Utils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * wsamiaw InvManager — auto-armor, hotbar layout, optional trash drop while inventory is open.
 */
public final class InvManagerModule extends Module {

    private final SliderSetting minDelay = addSetting(new SliderSetting("Min delay", 1f, 0f, 20f, 1f));
    private final SliderSetting maxDelay = addSetting(new SliderSetting("Max delay", 2f, 0f, 20f, 1f));
    private final SliderSetting openDelay = addSetting(new SliderSetting("Open delay", 1f, 0f, 20f, 1f));
    private final BoolSetting autoArmor = addSetting(new BoolSetting("Auto armor", true));
    private final SliderSetting autoArmorInterval = addSetting(
            new SliderSetting("Auto armor interval", 0f, 0f, 100f, 1f));
    private final BoolSetting dropTrash = addSetting(new BoolSetting("Drop trash", false));
    private final BoolSetting checkDurability = addSetting(new BoolSetting("Check durability", true));
    /** Hotbar preferences: 0 = disabled, 1–9 = hotbar slots 1–9. */
    private final SliderSetting swordSlot = addSetting(new SliderSetting("Sword slot", 1f, 0f, 9f, 1f));
    private final SliderSetting pickaxeSlot = addSetting(new SliderSetting("Pickaxe slot", 3f, 0f, 9f, 1f));
    private final SliderSetting shovelSlot = addSetting(new SliderSetting("Shovel slot", 4f, 0f, 9f, 1f));
    private final SliderSetting axeSlot = addSetting(new SliderSetting("Axe slot", 5f, 0f, 9f, 1f));
    private final SliderSetting blocksSlot = addSetting(new SliderSetting("Blocks slot", 2f, 0f, 9f, 1f));
    private final SliderSetting blocks = addSetting(new SliderSetting("Blocks", 128f, 64f, 2304f, 16f));
    private final SliderSetting projectileSlot = addSetting(new SliderSetting("Projectile slot", 7f, 0f, 9f, 1f));
    private final SliderSetting projectiles = addSetting(new SliderSetting("Projectiles", 64f, 16f, 2304f, 16f));
    private final SliderSetting goldAppleSlot = addSetting(new SliderSetting("Gold apple slot", 9f, 0f, 9f, 1f));
    private final SliderSetting arrow = addSetting(new SliderSetting("Arrow", 256f, 0f, 2304f, 16f));
    private final SliderSetting bowSlot = addSetting(new SliderSetting("Bow slot", 8f, 0f, 9f, 1f));

    private int actionDelay;
    private int oDelay;
    private boolean inventoryOpen;
    private long autoArmorTimeMs;

    public InvManagerModule() {
        super("InvManager", "Sorts inventory, equips armor, and drops trash", Category.PLAYER);
        autoArmorInterval.visibleWhen(autoArmor::getValue);
    }

    public int getBlocksKeep() {
        return Math.round(blocks.getValue());
    }

    public int getProjectilesKeep() {
        return Math.round(projectiles.getValue());
    }

    public int getArrowKeep() {
        return Math.round(arrow.getValue());
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
        actionDelay = 0;
        oDelay = 0;
        inventoryOpen = false;
        autoArmorTimeMs = 0L;
    }

    /**
     * PRE-style (before living update / {@code C03}): window clicks after flying flag Grim
     * {@code Post} on 1.8 ({@code click window} / held-item-change family).
     */
    @Override
    public void onTickStart() {
        if (actionDelay > 0)
            actionDelay--;
        if (oDelay > 0)
            oDelay--;

        EntityPlayerSP player = Mc.player();
        if (player == null || !isValidGameMode()) {
            inventoryOpen = false;
            return;
        }

        if (!(Mc.currentScreen() instanceof GuiInventory)) {
            inventoryOpen = false;
            return;
        }
        GuiInventory gui = (GuiInventory) Mc.currentScreen();
        if (!(gui.inventorySlots instanceof ContainerPlayer)) {
            inventoryOpen = false;
            return;
        }

        if (!inventoryOpen) {
            inventoryOpen = true;
            oDelay = Math.round(openDelay.getValue()) + 1;
            autoArmorTimeMs = System.currentTimeMillis();
        }
        if (oDelay > 0 || actionDelay > 0)
            return;

        ArrayList<Integer> equippedArmorSlots = new ArrayList<Integer>(Arrays.asList(-1, -1, -1, -1));
        ArrayList<Integer> inventoryArmorSlots = new ArrayList<Integer>(Arrays.asList(-1, -1, -1, -1));
        for (int i = 0; i < 4; i++) {
            equippedArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, true));
            inventoryArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, false));
        }

        boolean durability = checkDurability.getValue();
        int preferredSword = hotbarPref(swordSlot);
        int inventorySword = ItemUtil.findSwordInInventorySlot(preferredSword, durability);
        if (inventorySword == -1)
            inventorySword = ItemUtil.findSwordInInventorySlot(preferredSword, false);

        int preferredPickaxe = hotbarPref(pickaxeSlot);
        int inventoryPickaxe = ItemUtil.findInventorySlot("pickaxe", preferredPickaxe, durability);
        if (inventoryPickaxe == -1)
            inventoryPickaxe = ItemUtil.findInventorySlot("pickaxe", preferredPickaxe, false);

        int preferredShovel = hotbarPref(shovelSlot);
        int inventoryShovel = ItemUtil.findInventorySlot("shovel", preferredShovel, durability);
        if (inventoryShovel == -1)
            inventoryShovel = ItemUtil.findInventorySlot("shovel", preferredShovel, false);

        int preferredAxe = hotbarPref(axeSlot);
        int inventoryAxe = ItemUtil.findInventorySlot("axe", preferredAxe, durability);
        if (inventoryAxe == -1)
            inventoryAxe = ItemUtil.findInventorySlot("axe", preferredAxe, false);

        int preferredBlocks = hotbarPref(blocksSlot);
        int inventoryBlocks = ItemUtil.findInventorySlot(preferredBlocks, ItemUtil.ItemType.Block);

        int preferredProjectile = hotbarPref(projectileSlot);
        int inventoryProjectile = ItemUtil.findInventorySlot(preferredProjectile, ItemUtil.ItemType.Projectile);
        if (inventoryProjectile == -1)
            inventoryProjectile = ItemUtil.findInventorySlot(preferredProjectile, ItemUtil.ItemType.FishRod);

        int preferredGoldApple = hotbarPref(goldAppleSlot);
        int inventoryGoldApple = ItemUtil.findInventorySlot(preferredGoldApple, ItemUtil.ItemType.GoldApple);

        int preferredBow = hotbarPref(bowSlot);
        int inventoryBow = ItemUtil.findBowInventorySlot(preferredBow, durability);
        if (inventoryBow == -1)
            inventoryBow = ItemUtil.findBowInventorySlot(preferredBow, false);

        int windowId = player.inventoryContainer.windowId;
        long armorIntervalMs = Math.round(autoArmorInterval.getValue()) * 50L;

        if (autoArmor.getValue() && System.currentTimeMillis() - autoArmorTimeMs >= armorIntervalMs) {
            for (int i = 0; i < 4; i++) {
                int equippedSlot = equippedArmorSlots.get(i);
                int inventorySlot = inventoryArmorSlots.get(i);
                if (equippedSlot == -1 && inventorySlot == -1)
                    continue;
                int playerArmorSlot = 39 - i;
                if (equippedSlot == playerArmorSlot || inventorySlot == playerArmorSlot)
                    continue;
                if (player.inventory.getStackInSlot(playerArmorSlot) != null) {
                    if (player.inventory.getFirstEmptyStack() != -1)
                        click(windowId, convertSlot(playerArmorSlot), 0, 1);
                    else
                        click(windowId, convertSlot(playerArmorSlot), 1, 4);
                } else {
                    int armorToEquip = equippedSlot != -1 ? equippedSlot : inventorySlot;
                    click(windowId, convertSlot(armorToEquip), 0, 1);
                    autoArmorTimeMs = System.currentTimeMillis();
                }
                return;
            }
        }

        LinkedHashSet<Integer> usedHotbar = new LinkedHashSet<Integer>();
        if (swapHotbar(windowId, preferredSword, inventorySword, usedHotbar))
            return;
        if (swapHotbar(windowId, preferredPickaxe, inventoryPickaxe, usedHotbar))
            return;
        if (swapHotbar(windowId, preferredShovel, inventoryShovel, usedHotbar))
            return;
        if (swapHotbar(windowId, preferredAxe, inventoryAxe, usedHotbar))
            return;
        if (swapHotbar(windowId, preferredBlocks, inventoryBlocks, usedHotbar))
            return;
        if (swapHotbar(windowId, preferredProjectile, inventoryProjectile, usedHotbar))
            return;
        if (swapHotbar(windowId, preferredGoldApple, inventoryGoldApple, usedHotbar))
            return;
        if (swapHotbar(windowId, preferredBow, inventoryBow, usedHotbar))
            return;

        if (!dropTrash.getValue())
            return;

        int currentBlockCount = stackSize(inventoryBlocks);
        int currentProjectileCount = stackSize(inventoryProjectile);
        for (int i = 0; i < 36; i++) {
            if (equippedArmorSlots.contains(i) || inventoryArmorSlots.contains(i)
                    || inventorySword == i || inventoryPickaxe == i || inventoryShovel == i
                    || inventoryAxe == i || inventoryBlocks == i || inventoryProjectile == i
                    || inventoryGoldApple == i || inventoryBow == i)
                continue;
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack == null)
                continue;
            boolean isBlock = ItemUtil.isBlock(stack);
            boolean isProjectile = ItemUtil.isProjectile(stack);
            if (isBlock)
                currentBlockCount += stack.stackSize;
            if (isProjectile)
                currentProjectileCount += stack.stackSize;
            boolean drop;
            if (isBlock)
                drop = currentBlockCount > getBlocksKeep();
            else if (isProjectile)
                drop = currentProjectileCount > getProjectilesKeep();
            else
                drop = ItemUtil.isNotSpecialItem(stack);
            if (drop) {
                click(windowId, convertSlot(i), 1, 4);
                return;
            }
        }
    }

    private boolean swapHotbar(int windowId, int preferredHotbar, int inventorySlot,
            LinkedHashSet<Integer> usedHotbar) {
        if (preferredHotbar < 0 || preferredHotbar > 8 || inventorySlot == -1)
            return false;
        if (usedHotbar.contains(preferredHotbar))
            return false;
        usedHotbar.add(preferredHotbar);
        if (inventorySlot == preferredHotbar)
            return false;
        click(windowId, convertSlot(inventorySlot), preferredHotbar, 2);
        return true;
    }

    private void click(int windowId, int slotId, int button, int mode) {
        if (Mc.windowClick(windowId, slotId, button, mode))
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
        actionDelay = Utils.randomizeInt(min + 1, max + 1);
    }

    private static int hotbarPref(SliderSetting setting) {
        return Math.round(setting.getValue()) - 1;
    }

    private static int convertSlot(int invSlot) {
        return Mc.inventoryToContainerSlot(invSlot);
    }

    private static int stackSize(int slot) {
        if (slot == -1)
            return 0;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return 0;
        ItemStack stack = player.inventory.getStackInSlot(slot);
        return stack != null ? stack.stackSize : 0;
    }

    private static boolean isValidGameMode() {
        if (Mc.controller() == null)
            return false;
        WorldSettings.GameType type = Mc.controller().getCurrentGameType();
        return type == WorldSettings.GameType.SURVIVAL || type == WorldSettings.GameType.ADVENTURE;
    }
}
