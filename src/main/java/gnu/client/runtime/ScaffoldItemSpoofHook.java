package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * Scaffold item spoof — <b>render-only</b>.
 *
 * <p>Gameplay / server stay on the block hotbar slot. Visuals (hotbar highlight, held-item
 * overlay, first-person {@code ItemRenderer} cache) read the slot from when Scaffold was
 * enabled (sword, etc.).
 *
 * <p>Never mutates {@code inventory.currentItem} for rendering. Mutating it was causing
 * {@code syncCurrentPlayItem} to C09 the visual slot while the client still predicted
 * places with blocks → ghost blocks.
 */
public final class ScaffoldItemSpoofHook {

    private ScaffoldItemSpoofHook() {}

    public static boolean isActive() {
        ScaffoldModule scaffold = active();
        if (scaffold == null)
            return false;
        int spoof = scaffold.getSpoofSlot();
        return spoof >= 0 && spoof <= 8;
    }

    /** Visual hotbar index, or {@code -1} when inactive. */
    public static int getSpoofSlot() {
        ScaffoldModule scaffold = active();
        if (scaffold == null)
            return -1;
        int spoof = scaffold.getSpoofSlot();
        return spoof >= 0 && spoof <= 8 ? spoof : -1;
    }

    /**
     * When active, returns the spoof-slot stack (may be {@code null}).
     * Otherwise {@link InventoryPlayer#getCurrentItem()}.
     */
    public static ItemStack redirectCurrentItem(InventoryPlayer inventory) {
        if (inventory == null)
            return null;
        if (isActive()) {
            int spoof = getSpoofSlot();
            return Mc.getStackInSlot(inventory, spoof);
        }
        return inventory.getCurrentItem();
    }

    /** @deprecated mutation removed — kept so old call sites compile until cleaned up */
    public static boolean isRenderSpoofing() {
        return false;
    }

    /** @deprecated no-op — render spoof must not mutate {@code currentItem} */
    public static void beginRenderSlotSpoof() {}

    /** @deprecated no-op — render spoof must not mutate {@code currentItem} */
    public static void endRenderSlotSpoof() {}

    private static ScaffoldModule active() {
        Module m = ModuleManager.instance().getModule("Scaffold");
        if (!(m instanceof ScaffoldModule) || !m.isEnabled())
            return null;
        return (ScaffoldModule) m;
    }
}
