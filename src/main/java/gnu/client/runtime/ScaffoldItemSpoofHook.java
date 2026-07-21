package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.player.InventoryPlayer;

public final class ScaffoldItemSpoofHook {
    private ScaffoldItemSpoofHook() {}

    /**
     * True when Scaffold is enabled with a valid spoof hotbar index.
     * Callers must honor a null stack from {@link #redirectCurrentItem} in this state
     * (empty spoof slot) instead of falling back to {@code getCurrentItem()}.
     */
    public static boolean isActive() {
        ScaffoldModule scaffold = active();
        if (scaffold == null)
            return false;
        int spoof = scaffold.getSpoofSlot();
        return spoof >= 0 && spoof <= 8;
    }

    /**
     * When {@link #isActive()}, returns the spoof-slot stack (may be {@code null}).
     * Otherwise returns {@link InventoryPlayer#getCurrentItem()}.
     */
    public static Object redirectCurrentItem(Object inventoryPlayer) {
        if (!(inventoryPlayer instanceof InventoryPlayer))
            return null;
        InventoryPlayer inventory = (InventoryPlayer) inventoryPlayer;
        if (isActive()) {
            ScaffoldModule scaffold = active();
            return Mc.getStackInSlot(inventory, scaffold.getSpoofSlot());
        }
        return inventory.getCurrentItem();
    }

    private static ScaffoldModule active() {
        Module m = ModuleManager.instance().getModule("Scaffold");
        if (!(m instanceof ScaffoldModule) || !m.isEnabled())
            return null;
        return (ScaffoldModule) m;
    }
}
