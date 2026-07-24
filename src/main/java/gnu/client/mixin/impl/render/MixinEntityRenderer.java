package gnu.client.mixin.impl.render;

import gnu.client.event.PostMouseSelectionEvent;
import gnu.client.helper.RotationHelper;
import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.runtime.AutoBlockPoseHook;
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

    /** OpenMyau fake-block pose for KA AB — logic in {@link AutoBlockPoseHook}. */
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void gnu$forceAutoBlockPose(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        AutoBlockPoseHook.beginFrame();
    }

    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void gnu$restoreAutoBlockPose(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        AutoBlockPoseHook.endFrame();
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

    /**
     * Clear Weather: skip rain/snow rendering entirely.
     */
    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
    private void gnu$clearWeather(float partialTicks, CallbackInfo ci) {
        if (PerformanceModule.clearWeather()) {
            ci.cancel();
        }
    }

    /**
     * Skip heavy weather (rain/snow) pass while a fullscreen GUI (e.g. ClickGUI) covers the
     * world — the world is hidden anyway, so the cost is pure waste.
     */
    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
    private void gnu$skipWeatherWhenGuiOpen(CallbackInfo ci) {
        if (!PerformanceModule.skipWorldWhenGuiOpen())
            return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            ci.cancel();
        }
    }

    /**
     * Skip cloud rendering while a fullscreen GUI covers the world — the player can't see
     * the sky anyway, so the cloud pass is pure waste. Mirrors the weather-skip toggle.
     * Note: the MCP method is {@code renderCloudsCheck(RenderGlobal, float, int)}, not
     * {@code renderClouds}.
     */
    @Inject(method = "renderCloudsCheck", at = @At("HEAD"), cancellable = true)
    private void gnu$skipCloudsWhenGuiOpen(CallbackInfo ci) {
        if (!PerformanceModule.skipCloudsWhenGuiOpen())
            return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            ci.cancel();
        }
    }

    /**
     * No Hurt Cam: cancel the camera shake/zoom applied when the player takes damage.
     * Purely a view effect — no world/entity state touched, so it's safe alongside OptiFine.
     */
    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void gnu$noHurtCam(CallbackInfo ci) {
        if (PerformanceModule.noHurtCam()) {
            ci.cancel();
        }
    }
}
