package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.PerformanceModule;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Caps live particles to reduce per-frame tessellation and GC churn on busy servers.
 * Mirrors the particle-reduction toggles found in non-cheat performance clients.
 *
 * <p>The particle store is {@code EffectRenderer.fxLayers} ({@code List[][]}); we count
 * its total size once per spawn attempt. The field is resolved by reflection a single
 * time (cached) since the MCP name for this mapping set differs from the canonical one.
 */
@SideOnly(Side.CLIENT)
@Mixin(EffectRenderer.class)
public abstract class MixinEffectRenderer {

    private static Field gnu$fxLayersField;
    private static boolean gnu$fxLayersResolved;

    private static int gnu$particleCount(EffectRenderer renderer) {
        if (!gnu$fxLayersResolved) {
            gnu$fxLayersResolved = true;
            try {
                Field f = EffectRenderer.class.getDeclaredField("fxLayers");
                f.setAccessible(true);
                gnu$fxLayersField = f;
            } catch (NoSuchFieldException ignored) {
                gnu$fxLayersField = null;
            }
        }
        if (gnu$fxLayersField == null)
            return 0;
        try {
            List<?>[][] layers = (List<?>[][]) gnu$fxLayersField.get(renderer);
            if (layers == null)
                return 0;
            int total = 0;
            for (List<?>[] row : layers) {
                if (row == null)
                    continue;
                for (List<?> bucket : row) {
                    if (bucket != null)
                        total += bucket.size();
                }
            }
            return total;
        } catch (IllegalAccessException ignored) {
            return 0;
        }
    }

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void gnu$capParticles(EntityFX effect, CallbackInfo ci) {
        if (!PerformanceModule.reducedParticles())
            return;
        if (gnu$particleCount((EffectRenderer) (Object) this) >= PerformanceModule.particleLimit()) {
            ci.cancel();
        }
    }
}
