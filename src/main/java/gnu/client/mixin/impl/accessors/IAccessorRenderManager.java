package gnu.client.mixin.impl.accessors;

import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderManager.class)
public interface IAccessorRenderManager {
    @Accessor("playerViewY")
    float getPlayerViewY();

    @Accessor("playerViewY")
    void setPlayerViewY(float yaw);

    @Accessor("playerViewX")
    float getPlayerViewX();

    @Accessor("playerViewX")
    void setPlayerViewX(float pitch);

    @Accessor("renderPosX")
    double getRenderPosX();

    @Accessor("renderPosY")
    double getRenderPosY();

    @Accessor("renderPosZ")
    double getRenderPosZ();
}
