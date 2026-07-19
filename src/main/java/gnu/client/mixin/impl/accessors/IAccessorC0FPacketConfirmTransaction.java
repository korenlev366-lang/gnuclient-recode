package gnu.client.mixin.impl.accessors;

import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C0FPacketConfirmTransaction.class)
public interface IAccessorC0FPacketConfirmTransaction {
    @Accessor("uid")
    short getUid();

    @Accessor("uid")
    void setUid(short uid);
}
