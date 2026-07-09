package gnu.client.mixin.impl.accessors;

import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameSettings.class)
public interface IAccessorGameSettings {
    @Accessor("thirdPersonView")
    int getThirdPersonView();

    @Accessor("thirdPersonView")
    void setThirdPersonView(int view);

    @Accessor("fovSetting")
    float getFovSetting();

    @Accessor("fovSetting")
    void setFovSetting(float fov);
}
