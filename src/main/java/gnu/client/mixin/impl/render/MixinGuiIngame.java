package gnu.client.mixin.impl.render;

import gnu.client.ui.hud.HudScoreboard;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lux HUD scoreboard replacement.
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
}
