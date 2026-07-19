package gnu.client.script;

import gnu.client.runtime.mc.Mc;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Script-facing {@code inventory} accessor — hotbar/inventory reads, armor,
 * window clicks, and tool selection.
 */
public final class Inventory {

    public static final Inventory INSTANCE = new Inventory();

    private Inventory() {}

    /**
     * ItemStack in the given inventory slot, or {@code null}.
     *
     * @param slot 0–35 main/hotbar, 36–39 armor (boots→helmet)
     */
    public Object getStackInSlot(int slot) {
        EntityPlayer player = Mc.player();
        if (player == null || player.inventory == null)
            return null;
        return Mc.getStackInSlot(player.inventory, slot);
    }

    /** Current hotbar index (0–8), or {@code -1} if unavailable. */
    public int getSlot() {
        return Mc.getHotbarSlot(Mc.player());
    }

    public void setSlot(int hotbarSlot) {
        Mc.setHotbarSlot(Mc.player(), hotbarSlot);
    }

    public boolean isSoup(Object stack) {
        return Mc.isSoup(asStack(stack));
    }

    public boolean isFood(Object stack) {
        return Mc.isFood(asStack(stack));
    }

    public boolean isArmor(Object stack) {
        return Mc.isArmor(asStack(stack));
    }

    public String getItemName(Object stack) {
        return Mc.getItemName(asStack(stack));
    }

    /** Armor slot 0=boots … 3=helmet. */
    public Object getArmorStack(int armorSlot) {
        return Mc.getArmorStack(armorSlot);
    }

    public int getArmorValue(Object stack) {
        return Mc.getArmorValue(asStack(stack));
    }

    public int getArmorType(Object stack) {
        return Mc.getArmorType(asStack(stack));
    }

    public boolean windowClick(int windowId, int slotId, int button, int mode) {
        return Mc.windowClick(windowId, slotId, button, mode);
    }

    public boolean clickPlayerSlot(int containerSlot, int button, int mode) {
        return Mc.clickPlayerSlot(containerSlot, button, mode);
    }

    public int inventoryToContainerSlot(int invSlot) {
        return Mc.inventoryToContainerSlot(invSlot);
    }

    /** Equip better armor from inventory into worn slots. Returns pieces swapped. */
    public int equipBestArmor() {
        return Mc.equipBestArmor();
    }

    /**
     * Best hotbar tool for a block state/block object, or {@code -1}.
     * Accepts {@link IBlockState}, {@link Block}, or null.
     */
    public int findBestHotbarTool(Object blockOrState) {
        Block block = asBlock(blockOrState);
        return Mc.findBestHotbarTool(block);
    }

    private static ItemStack asStack(Object stack) {
        return stack instanceof ItemStack ? (ItemStack) stack : null;
    }

    private static Block asBlock(Object blockOrState) {
        if (blockOrState instanceof Block)
            return (Block) blockOrState;
        if (blockOrState instanceof IBlockState)
            return ((IBlockState) blockOrState).getBlock();
        return null;
    }
}
