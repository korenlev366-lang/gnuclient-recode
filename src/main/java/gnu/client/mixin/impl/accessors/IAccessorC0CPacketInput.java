package gnu.client.mixin.impl.accessors;

import net.minecraft.network.play.client.C0CPacketInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C0CPacketInput.class)
public interface IAccessorC0CPacketInput {
    @Accessor("strafeSpeed")
    void setStrafeSpeed(float strafeSpeed);

    @Accessor("forwardSpeed")
    void setForwardSpeed(float forwardSpeed);

    @Accessor("jumping")
    void setJumping(boolean jumping);
}
