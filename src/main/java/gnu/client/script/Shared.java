package gnu.client.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cross-script shared key/value store and simple pub/sub bus.
 * Keys are global across all scripts in the process.
 */
public final class Shared {

    public static final Shared INSTANCE = new Shared();

    private final Map<String, Object> store = new ConcurrentHashMap<String, Object>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public interface Listener {
        void onShared(String channel, Object payload);
    }

    private Shared() {}

    public void put(String key, Object value) {
        if (key == null)
            return;
        if (value == null)
            store.remove(key);
        else
            store.put(key, value);
    }

    public Object get(String key) {
        return key == null ? null : store.get(key);
    }

    public String getString(String key, String fallback) {
        Object v = get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    public boolean getBool(String key, boolean fallback) {
        Object v = get(key);
        if (v instanceof Boolean)
            return (Boolean) v;
        return fallback;
    }

    public float getFloat(String key, float fallback) {
        Object v = get(key);
        if (v instanceof Number)
            return ((Number) v).floatValue();
        return fallback;
    }

    public int getInt(String key, int fallback) {
        Object v = get(key);
        if (v instanceof Number)
            return ((Number) v).intValue();
        return fallback;
    }

    public boolean has(String key) {
        return key != null && store.containsKey(key);
    }

    public void remove(String key) {
        if (key != null)
            store.remove(key);
    }

    public void clear() {
        store.clear();
    }

    /** Publish to all scripts that implement {@code onShared(String, Object)}. */
    public void emit(String channel, Object payload) {
        if (channel == null)
            return;
        ScriptManager.instance().dispatchShared(channel, payload);
        for (Listener listener : listeners) {
            try {
                listener.onShared(channel, payload);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Internal/native listener hook (tests / advanced). */
    public void addListener(Listener listener) {
        if (listener != null)
            listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
