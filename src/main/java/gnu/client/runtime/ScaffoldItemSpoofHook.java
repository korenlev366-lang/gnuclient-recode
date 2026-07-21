package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.player.InventoryPlayer;

public final class ScaffoldItemSpoofHook {
    private ScaffoldItemSpoofHook() {}

    public static Object redirectCurrentItem(Object inventoryPlayer) {
        if (!(inventoryPlayer instanceof InventoryPlayer))
            return null;
        InventoryPlayer inventory = (InventoryPlayer) inventoryPlayer;
        ScaffoldModule scaffold = active();
        if (scaffold != null) {
            int spoof = scaffold.getSpoofSlot();
            if (spoof >= 0 && spoof <= 8)
                return Mc.getStackInSlot(inventory, spoof);
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
