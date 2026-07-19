package gnu.client.mixin.impl.accessors;

import net.minecraft.network.play.client.C00PacketKeepAlive;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C00PacketKeepAlive.class)
public interface IAccessorC00PacketKeepAlive {
    @Accessor("key")
    int getKey();

    @Accessor("key")
    void setKey(int key);
}
