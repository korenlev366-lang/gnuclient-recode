package gnu.client.mixin.impl.accessors;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerControllerMP.class)
public interface IAccessorPlayerControllerMP {
    @Accessor("blockHitDelay")
    int getBlockHitDelay();

    @Accessor("blockHitDelay")
    void setBlockHitDelay(int delay);

    @Accessor("currentPlayerItem")
    int getCurrentPlayerItem();

    @Invoker("syncCurrentPlayItem")
    void invokeSyncCurrentPlayItem();
}
