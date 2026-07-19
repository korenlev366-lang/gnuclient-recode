package gnu.client.ui.hud;

import gnu.client.config.ConfigManager;
import gnu.client.module.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Toast notification queue: newest-at-bottom stacking, live/exit caps, hold+exit timing.
 * Ingress never trusts a caller-supplied enable flag — {@link #pushStateChange(Module)}
 * samples {@link Module#isEnabled()} at enqueue time.
 */
public final class NotificationQueue {

    public static final int LIVE_CAP = 8;
    public static final int EXIT_CAP = 8;
    public static final long HOLD_NS = 2_500_000_000L;
    public static final long EXIT_NS = 400_000_000L;

    private final Object lock = new Object();
    private final List<Entry> live = new ArrayList<Entry>();
    private final List<Entry> exiting = new ArrayList<Entry>();
    private boolean suppress;

    /** Snapshot for rendering (immutable fields; anim floats updated on render thread). */
    public static final class Entry {
        public final String moduleName;
        public final boolean enabled;
        public final long createdAtNs;
        long exitStartedAtNs = -1L;
        float animIn = 0f;
        float animOut = 0f;
        float layoutY = 0f;
        float targetY = 0f;

        Entry(String moduleName, boolean enabled, long createdAtNs) {
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.createdAtNs = createdAtNs;
        }

        public boolean isExiting() {
            return exitStartedAtNs >= 0L;
        }

        public long ageNs(long nowNs) {
            return nowNs - createdAtNs;
        }

        /** 1 → 0 over hold; clamped. */
        public float progress(long nowNs) {
            if (isExiting()) {
                return 0f;
            }
            long age = ageNs(nowNs);
            if (age >= HOLD_NS) {
                return 0f;
            }
            return 1f - (age / (float) HOLD_NS);
        }

        public float alpha() {
            if (isExiting()) {
                return Math.max(0f, 1f - animOut);
            }
            return animIn;
        }
    }

    public void setSuppress(boolean suppress) {
        synchronized (lock) {
            this.suppress = suppress;
        }
    }

    public boolean isSuppress() {
        synchronized (lock) {
            return suppress;
        }
    }

    /**
     * Enqueue a toast for {@code module}'s <em>current</em> enable state.
     * No-op while config is loading or suppress is set.
     */
    public void pushStateChange(Module module) {
        if (module == null) {
            return;
        }
        if (ConfigManager.instance().isLoading()) {
            return;
        }
        enqueue(module.getName(), module.isEnabled(), System.nanoTime());
    }

    /** Custom toast for scripts / non-module callers. */
    public void pushToast(String title, boolean enabled) {
        if (title == null || title.isEmpty()) {
            return;
        }
        if (ConfigManager.instance().isLoading()) {
            return;
        }
        enqueue(title, enabled, System.nanoTime());
    }

    /** Package/test entry: enqueue with explicit final state (already sampled). */
    void enqueue(String moduleName, boolean enabled, long nowNs) {
        if (moduleName == null || moduleName.isEmpty()) {
            return;
        }
        synchronized (lock) {
            if (suppress) {
                return;
            }
            live.add(new Entry(moduleName, enabled, nowNs));
            while (live.size() > LIVE_CAP) {
                Entry dropped = live.remove(0);
                beginExit(dropped, nowNs);
            }
        }
    }

    private void beginExit(Entry entry, long nowNs) {
        if (entry.isExiting()) {
            return;
        }
        entry.exitStartedAtNs = nowNs;
        entry.animOut = 0f;
        exiting.add(entry);
        while (exiting.size() > EXIT_CAP) {
            exiting.remove(0);
        }
    }

    /**
     * Advance hold/exit timers. Call once per frame with current {@code nanoTime}.
     */
    public void advance(long nowNs) {
        synchronized (lock) {
            for (int i = live.size() - 1; i >= 0; i--) {
                Entry e = live.get(i);
                if (e.ageNs(nowNs) >= HOLD_NS) {
                    live.remove(i);
                    beginExit(e, nowNs);
                }
            }
            for (int i = exiting.size() - 1; i >= 0; i--) {
                Entry e = exiting.get(i);
                if (nowNs - e.exitStartedAtNs >= EXIT_NS) {
                    exiting.remove(i);
                }
            }
        }
    }

    /**
     * Bottom-first draw order: index 0 is nearest the bottom edge (newest live),
     * then older live, then exiting toasts stacked above.
     */
    public List<Entry> bottomFirst() {
        synchronized (lock) {
            List<Entry> bottomFirst = new ArrayList<Entry>();
            for (int i = live.size() - 1; i >= 0; i--) {
                bottomFirst.add(live.get(i));
            }
            for (int i = exiting.size() - 1; i >= 0; i--) {
                bottomFirst.add(exiting.get(i));
            }
            return bottomFirst;
        }
    }

    public int liveCount() {
        synchronized (lock) {
            return live.size();
        }
    }

    public int exitingCount() {
        synchronized (lock) {
            return exiting.size();
        }
    }

    public boolean hasActive() {
        synchronized (lock) {
            return !live.isEmpty() || !exiting.isEmpty();
        }
    }

    public void clearAll() {
        synchronized (lock) {
            live.clear();
            exiting.clear();
        }
    }

    /** Test helper: immutable copy of live entries oldest→newest. */
    List<Entry> liveSnapshot() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<Entry>(live));
        }
    }

    List<Entry> exitingSnapshot() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<Entry>(exiting));
        }
    }
}
