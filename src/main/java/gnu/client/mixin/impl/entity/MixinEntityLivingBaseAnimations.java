package gnu.client.mixin.impl.entity;

import gnu.client.module.modules.visual.AnimationsModule;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = EntityLivingBase.class, priority = 999)
public abstract class MixinEntityLivingBaseAnimations {

    @Inject(method = { "getArmSwingAnimationEnd", "func_82166_i" }, at = @At("HEAD"), cancellable = true)
    private void gnuAnimationsSwingSpeed(CallbackInfoReturnable<Integer> cir) {
        AnimationsModule mod = AnimationsModule.instance();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        cir.setReturnValue(AnimationsModule.armSwingAnimationEnd(mod.getSwingSpeedPct()));
    }
}
