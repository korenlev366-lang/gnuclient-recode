package gnu.client.mixin.impl.accessors;

import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NetworkPlayerInfo.class)
public interface IAccessorNetworkPlayerInfo {
    @Accessor("responseTime")
    int getResponseTime();

    @Accessor("responseTime")
    void setResponseTime(int responseTime);
}
