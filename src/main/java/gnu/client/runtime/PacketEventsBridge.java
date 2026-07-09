package gnu.client.runtime;

import gnu.client.event.ReceivePacketEvent;
import gnu.client.event.SendPacketEvent;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketUtil;
import gnu.client.utility.PacketUtils;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bridges Forge mixin packet events to legacy {@link PacketEvents} listeners used by modules.
 */
public final class PacketEventsBridge {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSend(SendPacketEvent event) {
        if (event.isCanceled() || PacketUtil.isDispatching())
            return;
        Object packet = event.getPacket();
        if (PacketEvents.onSend(packet)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onReceive(ReceivePacketEvent event) {
        if (event.isCanceled() || PacketUtil.isDispatching())
            return;
        if (PacketEvents.onReceive(event.getPacket())) {
            event.setCanceled(true);
        }
    }
}
