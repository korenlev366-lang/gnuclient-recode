package gnu.client.mixin.impl.entity;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.module.modules.combat.velocity.PolarVelocity;
import gnu.client.module.modules.movement.KeepSprintModule;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Attack hit-slow hooks: KeepSprint retain / packet-gap skip, plus Polar {@code 0.59928}.
 */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayerHitSlow {

    @ModifyConstant(
            method = "attackTargetEntityWithCurrentItem",
            constant = @Constant(doubleValue = 0.6)
    )
    private double gnuAttackHitSlow(double original) {
        EntityPlayer self = (EntityPlayer) (Object) this;
        if (!(self instanceof EntityPlayerSP))
            return original;

        double keep = KeepSprintModule.attackSlowMultiplier();
        if (keep != 0.6)
            return keep;

        if (self.hurtTime == 0)
            return original;
        Module mod = ModuleManager.instance().getModule("Velocity");
        if (!(mod instanceof VelocityModule))
            return original;
        if (!PolarVelocity.usesPolarHitSlow((VelocityModule) mod))
            return original;
        return PolarVelocity.POLAR_HIT_SLOW;
    }

    @Redirect(
            method = "attackTargetEntityWithCurrentItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"
            )
    )
    private void gnuKeepSprintFlag(EntityPlayer player, boolean sprinting) {
        if (KeepSprintModule.shouldKeepSprintFlag() && !sprinting) {
            KeepSprintModule.onAttackApplied();
            return;
        }
        player.setSprinting(sprinting);
        if (KeepSprintModule.shouldKeepSprintFlag())
            KeepSprintModule.onAttackApplied();
    }
}
