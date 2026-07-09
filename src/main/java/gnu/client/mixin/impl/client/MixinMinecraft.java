package gnu.client.mixin.impl.client;

import gnu.client.event.*;
import gnu.client.helper.RotationHelper;
import gnu.client.runtime.FreeLookHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.BEFORE))
    public void onBeforeGetMouseOver(CallbackInfo ci) {
        RotationHelper.get().updateServerRotations();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.AFTER))
    public void onRunTickMouseOver(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostMouseSelectionEvent());
    }

    @Inject(method = "runTick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/settings/GameSettings;chatVisibility:Lnet/minecraft/entity/player/EntityPlayer$EnumChatVisibility;"))
    private void injectBeforeChatVisibility(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PrePlayerInteractEvent());
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", ordinal = 2))
    private void onRunTick(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PreInputEvent());
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    public void onRunGameLoop(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RunGameLoopEvent());
    }

    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    public void injectClickMouse(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        PreAttackEvent preAttack = new PreAttackEvent(mc.objectMouseOver);
        MinecraftForge.EVENT_BUS.post(preAttack);
        if (preAttack.isCanceled()) {
            ci.cancel();
            return;
        }
        MinecraftForge.EVENT_BUS.post(new ClickMouseEvent());
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true)
    public void injectRightClickMouse(CallbackInfo ci) {
        RightClickMouseEvent event = new RightClickMouseEvent();
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    public void onRunTickStart(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new GameTickEvent());
    }

    @Inject(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/profiler/Profiler;startSection(Ljava/lang/String;)V",
            ordinal = 0,
            shift = At.Shift.BEFORE
        )
    )
    public void onRunTickAfterRightClickDelay(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RightClickDelayTickEvent());
    }

    @Inject(method = "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", at = @At("HEAD"))
    public void onDisplayGuiScreen(GuiScreen guiScreen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        GuiScreen previousGui = mc.currentScreen;
        GuiScreen setGui = guiScreen != null ? guiScreen : previousGui;
        MinecraftForge.EVENT_BUS.post(new GuiUpdateEvent(setGui, guiScreen != null));
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"))
    public void changeCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        PreSlotScrollEvent event = new PreSlotScrollEvent(slot, inventoryPlayer.currentItem);
        MinecraftForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            inventoryPlayer.changeCurrentItem(slot);
        }
    }

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", opcode = Opcodes.PUTFIELD))
    private void onSetThirdPersonView(GameSettings gameSettings, int value) {
        if (!FreeLookHook.isActive()) {
            gameSettings.thirdPersonView = value;
        }
    }

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.PUTFIELD))
    private void onSetCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        SlotUpdateEvent e = new SlotUpdateEvent(slot);
        MinecraftForge.EVENT_BUS.post(e);
        if (!e.isCanceled()) {
            inventoryPlayer.currentItem = slot;
        }
    }
}
