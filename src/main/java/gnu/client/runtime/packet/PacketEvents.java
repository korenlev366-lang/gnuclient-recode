package gnu.client.runtime.packet;

import gnu.client.common.GnuLog;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static entry points called from {@code MixinNetworkManager}'s
 * {@code @Inject} hooks on {@code NetworkManager}.
 */
public final class PacketEvents {

    private static final List<PacketListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, Long> LAST_ERROR_LOG_MS = new ConcurrentHashMap<>();
    private static final long ERROR_LOG_COOLDOWN_MS = 5000L;

    private PacketEvents() {}

    public static void register(PacketListener listener) {
        if (listener == null || LISTENERS.contains(listener))
            return;
        int priority = listener.sendPriority();
        int index = 0;
        for (; index < LISTENERS.size(); index++) {
            if (LISTENERS.get(index).sendPriority() < priority)
                break;
        }
        LISTENERS.add(index, listener);
    }

    public static void unregister(PacketListener listener) {
        LISTENERS.remove(listener);
    }

    /** Called from the mixin hook at the start of NetworkManager.sendPacket. Forward order by {@link PacketListener#sendPriority()}. */
    public static boolean onSend(Object packet) {
        if (PacketUtil.consumeFastTrack(packet))
            return false;
        if (PacketUtil.isDispatching() || !PacketHelper.isPacket(packet))
            return false;
        for (PacketListener listener : LISTENERS) {
            try {
                if (listener.onSend(packet))
                    return true;
            } catch (Throwable t) {
                logListenerError("onSend", listener, t);
            }
        }
        return false;
    }

    /** Called from the mixin hook at the start of NetworkManager.channelRead0 (after msg load). */
    public static boolean hookChannelRead(Object msg) {
        if (PacketUtil.isDispatching() || !PacketHelper.isPacket(msg))
            return false;
        return onReceive(msg);
    }

    public static boolean onReceive(Object packet) {
        if (PacketUtil.isDispatching())
            return false;
        for (PacketListener listener : LISTENERS) {
            try {
                if (listener.onReceive(packet))
                    return true;
            } catch (Throwable t) {
                logListenerError("onReceive", listener, t);
            }
        }
        return false;
    }

    private static void logListenerError(String side, PacketListener listener, Throwable t) {
        String name = listener != null ? listener.getClass().getName() : "null";
        String key = side + "|" + name + "|" + t.getClass().getName();
        long now = System.currentTimeMillis();
        Long prev = LAST_ERROR_LOG_MS.put(key, now);
        if (prev != null && now - prev < ERROR_LOG_COOLDOWN_MS)
            return;
        String where = "";
        StackTraceElement[] st = t.getStackTrace();
        if (st != null && st.length > 0)
            where = " at " + st[0];
        GnuLog.log("JAVA_ PacketEvents." + side + " listener error: " + name + " " + t + where);
    }
}
