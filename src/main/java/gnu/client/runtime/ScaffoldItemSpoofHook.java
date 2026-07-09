package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.player.ScaffoldModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * OpenMyau-style scaffold item-spoof render hooks.
 *
 * <p>Scaffold keeps {@code currentItem} on the block stack during gameplay
 * (OpenMyau parity). While item-spoof is active, rendering temporarily swaps
 * {@code currentItem} back to the pre-scaffold slot so the hand/hotbar show
 * the original item.
 */
public final class ScaffoldItemSpoofHook {

    private static int savedSlot = -1;
    private static int spoofDepth;

    private ScaffoldItemSpoofHook() {}

    /** True while render hooks temporarily swapped {@code currentItem}. */
    public static boolean isRenderSpoofActive() {
        return spoofDepth > 0;
    }

    public static void beginRenderSlotSpoof() {
        EntityPlayerSP player = Mc.player();
        ScaffoldModule scaffold = activeScaffold();
        if (player == null || scaffold == null)
            return;
        int spoof = scaffold.getSpoofSlot();
        if (spoof < 0 || spoof > 8)
            return;
        if (spoofDepth == 0) {
            savedSlot = Mc.getHotbarSlot(player);
            if (savedSlot != spoof)
                Mc.setHotbarSlot(player, spoof);
            else
                savedSlot = -1;
        }
        spoofDepth++;
    }

    public static void endRenderSlotSpoof() {
        if (spoofDepth <= 0)
            return;
        spoofDepth--;
        if (spoofDepth != 0 || savedSlot < 0)
            return;
        EntityPlayerSP player = Mc.player();
        if (player != null)
            Mc.setHotbarSlot(player, savedSlot);
        savedSlot = -1;
    }

    /**
     * Redirect for {@code InventoryPlayer.getCurrentItem()} inside
     * {@code GuiIngame.updateTick}.
     */
    public static Object redirectCurrentItem(Object inventoryPlayer) {
        if (!(inventoryPlayer instanceof InventoryPlayer))
            return null;
        InventoryPlayer inventory = (InventoryPlayer) inventoryPlayer;
        ScaffoldModule scaffold = activeScaffold();
        if (scaffold != null) {
            int spoof = scaffold.getSpoofSlot();
            if (spoof >= 0 && spoof <= 8)
                return Mc.getStackInSlot(inventory, spoof);
        }
        return inventory.getCurrentItem();
    }

    private static ScaffoldModule activeScaffold() {
        Module module = ModuleManager.instance().getModule("Scaffold");
        if (!(module instanceof ScaffoldModule) || !module.isEnabled())
            return null;
        ScaffoldModule scaffold = (ScaffoldModule) module;
        return scaffold.isItemSpoofEnabled() ? scaffold : null;
    }
}
