package gnu.client.lag.api;

import gnu.client.utility.IMinecraftInstance;
import gnu.client.utility.Utils;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings({"LambdaBodyCanBeCodeBlock", "unchecked"})
public enum EnumLagDirection implements IMinecraftInstance {
    INBOUND(
            packet -> {
                try {
                    ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
                } catch (final ThreadQuickExitException ignored) {
                    // minecraft uses an exception to indicate something getting scheduled... why?
                } catch (final Exception e) {
                    Utils.sendDebugMessage("error while handling packet: " + packet.getClass().getSimpleName());
                }
            }
    ),
    OUTBOUND(
            packet -> {
                mc.getNetHandler().addToSendQueue(packet);
            }
    );

    public static final Set<EnumLagDirection> ONLY_INBOUND = EnumSet.of(INBOUND);
    public static final Set<EnumLagDirection> ONLY_OUTBOUND = EnumSet.of(OUTBOUND);
    public static final Set<EnumLagDirection> BIDIRECTIONAL = EnumSet.allOf(EnumLagDirection.class);

    private final Consumer<Packet<?>> channel;

    EnumLagDirection(
            final Consumer<Packet<?>> channel
    ) {
        this.channel = channel;
    }

    public void passThroughChannel(final Packet<?> packet) {
        channel.accept(packet);
    }

}