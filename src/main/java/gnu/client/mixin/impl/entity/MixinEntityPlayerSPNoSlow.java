package gnu.client.mixin.impl.entity;

import gnu.client.module.modules.player.NoSlowModule;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSPNoSlow {

    @Redirect(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isUsingItem()Z"
            )
    )
    private boolean gnuNoSlowIsUsing(EntityPlayerSP self) {
        NoSlowModule noSlow = NoSlowModule.instance();
        if (noSlow != null && noSlow.isEnabled() && noSlow.isAnyActive())
            return false;
        return self.isUsingItem();
    }

    /** wsamiaw LivingUpdateEvent — apply motion % / sprint after use-slow is suppressed. */
    @Inject(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/AbstractClientPlayer;onLivingUpdate()V"
            )
    )
    private void gnuNoSlowLivingUpdate(CallbackInfo ci) {
        NoSlowModule noSlow = NoSlowModule.instance();
        if (noSlow != null)
            noSlow.applyLivingUpdateNoSlow();
    }
}
