package gnu.client.mixin.impl.render;

import gnu.client.runtime.ScaffoldItemSpoofHook;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * First-person hand uses the visual spoof stack while gameplay {@code currentItem}
 * stays on blocks. Feeds {@code itemToRender} via {@code updateEquippedItem} only —
 * no inventory mutation.
 */
@SideOnly(Side.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererScaffoldSpoof {

    @Redirect(
            method = "updateEquippedItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack gnu$spoofEquippedItem(InventoryPlayer inventory) {
        if (ScaffoldItemSpoofHook.isActive())
            return ScaffoldItemSpoofHook.redirectCurrentItem(inventory);
        return inventory.getCurrentItem();
    }
}
