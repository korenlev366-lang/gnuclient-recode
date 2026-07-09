package gnu.client.mixin.impl.accessors;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S12PacketEntityVelocity.class)
public interface IAccessorS12PacketEntityVelocity {
    @Accessor("motionX")
    void setMotionX(int motionX);

    @Accessor("motionY")
    void setMotionY(int motionY);

    @Accessor("motionZ")
    void setMotionZ(int motionZ);
}
