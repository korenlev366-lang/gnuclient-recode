package gnu.client.mixin.impl.render;

import gnu.client.runtime.ScaffoldItemSpoofHook;
import gnu.client.ui.hud.HudScoreboard;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

/**
 * Scaffold hotbar spoof + Lux HUD scoreboard replacement.
 */
@SideOnly(Side.CLIENT)
@Mixin(value = GuiIngame.class, priority = 9999)
public abstract class MixinGuiIngame {

    @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
    private void gnu$luxScoreboard(ScoreObjective objective, ScaledResolution scaledRes, CallbackInfo ci) {
        if (HudScoreboard.instance().tryRender(objective, scaledRes)) {
            ci.cancel();
        }
    }

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
