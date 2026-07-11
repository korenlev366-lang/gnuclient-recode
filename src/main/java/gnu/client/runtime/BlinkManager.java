package gnu.client.runtime;

import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.PacketUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Shared outbound blink ownership (OpenMyau BlinkManager).
 * AUTO_BLOCK used by KillAura blink modes; NO_SLOW reserved (do not activate this pass).
 */
public final class BlinkManager implements PacketListener {

    public static final BlinkManager INSTANCE = new BlinkManager();

    private BlinkModules blinkModule = BlinkModules.NONE;
    private boolean blinking;
    private final Deque<Object> blinkedPackets = new ArrayDeque<>();
    private Consumer<Object> flushSender = PacketUtil::sendPacketReleased;

    public BlinkManager() {}

    /** Test hook — default {@link PacketUtil#sendPacketReleased}. */
    public void setFlushSender(Consumer<Object> sender) {
        this.flushSender = sender != null ? sender : PacketUtil::sendPacketReleased;
    }

    /**
     * OpenMyau offer exemptions: hold nothing when idle; never hold keepalive/chat;
     * when the queue is empty, do not hold C0F. Does <em>not</em> use
     * {@link PacketHelper#isBlinkOutboundExempt} (that would drop C02 and break autoblock).
     */
    public boolean offerPacket(Object packet) {
        if (blinkModule == BlinkModules.NONE || packet == null)
            return false;
        if (PacketHelper.isKeepAlive(packet) || PacketHelper.isChat(packet))
            return false;
        if (blinkedPackets.isEmpty() && PacketHelper.isClientConfirmTransaction(packet))
            return false;
        blinkedPackets.offer(packet);
        return true;
    }

    public boolean setBlinkState(boolean state, BlinkModules module) {
        if (module == null || module == BlinkModules.NONE)
            return false;
        if (state) {
            blinkModule = module;
            blinking = true;
            return true;
        }
        if (blinkModule != module)
            return false;
        blinking = false;
        while (!blinkedPackets.isEmpty()) {
            Object p = blinkedPackets.poll();
            if (p != null)
                flushSender.accept(p);
        }
        blinkModule = BlinkModules.NONE;
        return true;
    }

    public BlinkModules getBlinkingModule() {
        return blinkModule;
    }

    public boolean isBlinking() {
        return blinking;
    }

    public int queuedCount() {
        return blinkedPackets.size();
    }

    @Override
    public boolean onSend(Object packet) {
        if (PacketUtil.isDispatching() || PacketUtil.consumeFastTrack(packet))
            return false;
        return offerPacket(packet);
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    /** Above typical module listeners so AUTO_BLOCK wins when active. */
    @Override
    public int sendPriority() {
        return 100;
    }
}
