package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(LayerHeldItem.class)
public abstract class MixinLayerHeldItem {

    @Inject(method = "doRenderLayer", at = @At("HEAD"), cancellable = true)
    private void gnu$fastPlayerModels(
            EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        if (!PerformanceModule.fastPlayerModels())
            return;
        if (!(entity instanceof EntityPlayer))
            return;
        if (entity == Mc.player())
            return;
        ci.cancel();
    }
}
