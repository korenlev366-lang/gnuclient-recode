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

/**
 * Scaffold item-spoof hotbar display (OpenMyau GuiIngame parity).
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
        Object spoofed = ScaffoldItemSpoofHook.redirectCurrentItem(inventoryPlayer);
        if (ScaffoldItemSpoofHook.isActive())
            return (ItemStack) spoofed; // may be null — empty spoof slot must not fall back
        return inventoryPlayer.getCurrentItem();
    }
}
