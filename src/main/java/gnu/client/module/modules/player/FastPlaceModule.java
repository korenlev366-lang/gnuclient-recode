package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Removes Minecraft's right-click placement delay, but only when
 * the held item is a block (ItemBlock). Other right-click actions
 * (food, potions, bows, tools, etc.) keep vanilla delay.
 *
 * Ported from RainClient {@code FastPlace} — modified to be block-only.
 */
public final class FastPlaceModule extends Module {

    public FastPlaceModule() {
        super("FastPlace", "Removes right-click delay for block placement only", Category.PLAYER);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void onTick() {
        if (!holdingBlock())
            return;

        Mc.clearRightClickDelay();
    }

    private static boolean holdingBlock() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        ItemStack stack = player.getHeldItem();
        if (stack == null)
            return false;

        return stack.getItem() instanceof ItemBlock;
    }
}
