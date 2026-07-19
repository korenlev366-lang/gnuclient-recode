package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Name-tag rendering controls. The nameplate methods live on the base {@link Render}
 * class (not {@code RenderManager}); canceling them here skips the plate for the common
 * render path used by most entity renderers.
 *
 * <ul>
 *   <li>{@code No Entity Names} — cancels all nameplate rendering.</li>
 *   <li>{@code Name Tag Distance} — skips nameplates beyond a distance cap, since a tag
 *       on a far entity is sub-pixel and pure waste. Uses squared distance to the camera
 *       (no sqrt) for the per-entity check.</li>
 * </ul>
 *
 * <p>Both are guarded by {@link PerformanceModule#noEntityNames()}, which already returns
 * false when OptiFine's Fast Render owns the render path.
 */
@SideOnly(Side.CLIENT)
@Mixin(Render.class)
public abstract class MixinRenderNames {

    @Inject(method = "renderName(Lnet/minecraft/entity/Entity;DDD)V", at = @At("HEAD"), cancellable = true)
    private void gnu$skipName(Entity entity, double x, double y, double z, CallbackInfo ci) {
        if (PerformanceModule.noEntityNames()) {
            ci.cancel();
            return;
        }
        if (entity != null && isBeyondNameTagRange(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLivingLabel(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V", at = @At("HEAD"),
            cancellable = true)
    private void gnu$skipLivingLabel(Entity entity, String str, double x, double y, double z, int maxDistance,
            CallbackInfo ci) {
        if (PerformanceModule.noEntityNames()) {
            ci.cancel();
            return;
        }
        if (entity != null && isBeyondNameTagRange(entity)) {
            ci.cancel();
        }
    }

    private static boolean isBeyondNameTagRange(Entity entity) {
        double[] vp = Mc.getViewerPos(1.0f);
        double dx = entity.posX - vp[0];
        double dy = entity.posY - vp[1];
        double dz = entity.posZ - vp[2];
        double distSq = dx * dx + dy * dy + dz * dz;
        float max = PerformanceModule.nameTagDistance();
        return distSq > (double) (max * max);
    }
}
