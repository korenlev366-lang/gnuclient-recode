package gnu.client.mixin.impl.entity;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.module.modules.combat.velocity.PolarVelocity;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * wsamiaw Polar Velocity: replace vanilla attack slow {@code 0.6} with {@code 0.59928}
 * while hurt.
 */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayerHitSlow {

    @ModifyConstant(
            method = "attackTargetEntityWithCurrentItem",
            constant = @Constant(doubleValue = 0.6)
    )
    private double gnuPolarHitSlow(double original) {
        EntityPlayer self = (EntityPlayer) (Object) this;
        if (!(self instanceof EntityPlayerSP))
            return original;
        if (self.hurtTime == 0)
            return original;
        Module mod = ModuleManager.instance().getModule("Velocity");
        if (!(mod instanceof VelocityModule) || !mod.isEnabled())
            return original;
        VelocityModule velocity = (VelocityModule) mod;
        if (!"Polar".equals(velocity.mode.getCurrentMode()))
            return original;
        return PolarVelocity.POLAR_HIT_SLOW;
    }
}
