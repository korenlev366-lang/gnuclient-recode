package gnu.client.mixin.impl.accessors;

import net.minecraft.network.play.client.C02PacketUseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C02PacketUseEntity.class)
public interface IAccessorC02PacketUseEntity {
    @Accessor("entityId")
    int getEntityId();
}
