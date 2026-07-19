package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.PerformanceModule;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Reduced Entity Distance: scales the entity render/update cutoff used by
 * {@code RenderManager} down to a fraction of the game's render distance. This is the
 * single biggest FPS lever on crowded servers and the standard entity-distance toggle in
 * non-cheat performance clients.
 */
@SideOnly(Side.CLIENT)
@Mixin(RenderManager.class)
public abstract class MixinRenderManagerCull {

    @Redirect(
            method = "doRenderEntity",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"))
    private int gnu$scaledRenderDistance(GameSettings gameSettings) {
        int base = gameSettings.renderDistanceChunks;
        if (!PerformanceModule.reducedEntityDistance())
            return base;
        return Math.max(2, (int) (base * PerformanceModule.entityDistanceFraction()));
    }
}
