package gnu.client.mixin.impl.accessors;

import net.minecraft.network.play.client.C03PacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C03PacketPlayer.class)
public interface IAccessorC03PacketPlayer {
    @Accessor("x")
    double getX();

    @Accessor("x")
    void setX(double x);

    @Accessor("y")
    double getY();

    @Accessor("y")
    void setY(double y);

    @Accessor("z")
    double getZ();

    @Accessor("z")
    void setZ(double z);

    @Accessor("yaw")
    float getYaw();

    @Accessor("yaw")
    void setYaw(float yaw);

    @Accessor("pitch")
    float getPitch();

    @Accessor("pitch")
    void setPitch(float pitch);

    @Accessor("onGround")
    boolean getOnGround();

    @Accessor("onGround")
    void setOnGround(boolean onGround);

    @Accessor("moving")
    boolean getMoving();

    @Accessor("moving")
    void setMoving(boolean moving);

    @Accessor("rotating")
    boolean getRotating();

    @Accessor("rotating")
    void setRotating(boolean rotating);
}
