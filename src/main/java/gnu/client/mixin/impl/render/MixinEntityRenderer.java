package gnu.client.mixin.impl.render;

import gnu.client.event.PostMouseSelectionEvent;
import gnu.client.helper.RotationHelper;
import gnu.client.runtime.FreeLookHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F"))
    private float freelookRotationYaw(Entity entity) {
        return FreeLookHook.isActive() ? FreeLookHook.redirectYaw(entity) : entity.rotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F"))
    private float freelookPrevRotationYaw(Entity entity) {
        return FreeLookHook.isActive() ? FreeLookHook.redirectYaw(entity) : entity.prevRotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F"))
    private float freelookRotationPitch(Entity entity) {
        return FreeLookHook.isActive() ? FreeLookHook.redirectPitch(entity) : entity.rotationPitch;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F"))
    private float freelookPrevRotationPitch(Entity entity) {
        return FreeLookHook.isActive() ? FreeLookHook.redirectPitch(entity) : entity.prevRotationPitch;
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;inGameHasFocus:Z"))
    private boolean freelookOverrideMouse(Minecraft mc) {
        return FreeLookHook.overrideMouse(mc);
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.AFTER))
    private void onRenderWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostMouseSelectionEvent());
    }

    @Inject(method = "getMouseOver", at = @At("HEAD"))
    private void onGetMouseOverHead(float partialTicks, CallbackInfo ci) {
        RotationHelper rh = RotationHelper.get();
        if (rh.swappedForMouseOver) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view != null && rh.isActive()) {
            Float yaw = rh.getServerYaw();
            Float pitch = rh.getServerPitch();
            if (yaw != null && !yaw.isNaN() && pitch != null && !pitch.isNaN()) {
                rh.beginSwap(view, yaw, pitch, true);
                rh.swappedForMouseOver = true;
            }
        }
    }

    @Inject(method = "getMouseOver", at = @At("RETURN"))
    private void onGetMouseOverReturn(float partialTicks, CallbackInfo ci) {
        RotationHelper rh = RotationHelper.get();
        if (rh.swappedForMouseOver) {
            Entity view = Minecraft.getMinecraft().getRenderViewEntity();
            if (view != null) {
                rh.endSwap(view);
            }
            rh.swappedForMouseOver = false;
        }
    }
}
