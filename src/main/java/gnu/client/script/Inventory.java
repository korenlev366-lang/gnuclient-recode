package gnu.client.script;

import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Script-facing {@code inventory} accessor — stateless singleton facade over
 * {@link Mc}. Exposes hotbar slot reads and per-slot stack queries.
 */
public final class Inventory {

    public static final Inventory INSTANCE = new Inventory();

    private Inventory() {}

    /**
     * ItemStack in the given hotbar/inventory slot, or {@code null} if the
     * slot is empty or the player is unavailable.
     *
     * @param slot 0–35 (0–8 hotbar)
     */
    public Object getStackInSlot(int slot) {
        EntityPlayer player = Mc.player();
        if (player == null || player.inventory == null)
            return null;
        ItemStack stack = Mc.getStackInSlot(player.inventory, slot);
        return stack;
    }

    /** Current hotbar index (0–8), or {@code -1} if unavailable. */
    public int getSlot() {
        return Mc.getHotbarSlot(Mc.player());
    }
}
