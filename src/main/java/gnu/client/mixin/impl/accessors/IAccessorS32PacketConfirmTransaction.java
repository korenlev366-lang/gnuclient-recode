package gnu.client.mixin.impl.accessors;

import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S32PacketConfirmTransaction.class)
public interface IAccessorS32PacketConfirmTransaction {
    @Accessor("actionNumber")
    short getActionNumber();

    @Accessor("actionNumber")
    void setActionNumber(short actionNumber);
}
