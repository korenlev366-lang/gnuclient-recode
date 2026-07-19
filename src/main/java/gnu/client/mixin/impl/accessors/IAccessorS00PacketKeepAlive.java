package gnu.client.mixin.impl.accessors;

import net.minecraft.network.play.server.S00PacketKeepAlive;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S00PacketKeepAlive.class)
public interface IAccessorS00PacketKeepAlive {
    @Accessor("id")
    int getId();

    @Accessor("id")
    void setId(int id);
}
