package gnu.client.mixin.impl.accessors;

import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Timer.class)
public interface IAccessorTimer {
    @Accessor("timerSpeed")
    float getTimerSpeed();

    @Accessor("timerSpeed")
    void setTimerSpeed(float speed);
}
