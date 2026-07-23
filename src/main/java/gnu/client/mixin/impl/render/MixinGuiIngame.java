package gnu.client.mixin.impl.render;

import gnu.client.runtime.ScaffoldItemSpoofHook;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;

/**
 * Scaffold item-spoof hotbar display — redirect only, never mutates {@code currentItem}.
 */
@SideOnly(Side.CLIENT)
@Mixin(value = GuiIngame.class, priority = 9999)
public abstract class MixinGuiIngame {

    @Redirect(
            method = "updateTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack gnu$spoofHotbarCurrentItem(InventoryPlayer inventoryPlayer) {
        if (ScaffoldItemSpoofHook.isActive())
            return ScaffoldItemSpoofHook.redirectCurrentItem(inventoryPlayer);
        return inventoryPlayer.getCurrentItem();
    }

    /** Selection diamond stays on the pre-Scaffold slot (sword, etc.). */
    @Redirect(
            method = "renderTooltip",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int gnu$spoofHotbarSelectedSlot(InventoryPlayer inventoryPlayer) {
        int spoof = ScaffoldItemSpoofHook.getSpoofSlot();
        if (spoof >= 0)
            return spoof;
        return inventoryPlayer.currentItem;
    }
}
