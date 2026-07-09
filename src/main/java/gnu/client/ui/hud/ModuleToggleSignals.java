package gnu.client.ui.hud;

import gnu.client.module.Module;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Identity-only dirty buffer for module enable-state changes. Callers must never
 * store or trust an enabled boolean here — HUD drains identities then samples
 * {@link Module#isEnabled()} as the source of truth.
 */
public final class ModuleToggleSignals {

    private static final Object LOCK = new Object();
    private static final Set<Module> DIRTY = Collections.newSetFromMap(new IdentityHashMap<Module, Boolean>());

    private ModuleToggleSignals() {
    }

    /** Record that {@code module}'s enable bit may have changed. Ignores null. */
    public static void mark(Module module) {
        if (module == null) {
            return;
        }
        synchronized (LOCK) {
            DIRTY.add(module);
        }
    }

    /**
     * Atomically copy and clear the dirty set. Returned set uses identity equality.
     */
    public static Set<Module> drain() {
        synchronized (LOCK) {
            if (DIRTY.isEmpty()) {
                return Collections.emptySet();
            }
            Set<Module> out = Collections.newSetFromMap(new IdentityHashMap<Module, Boolean>());
            out.addAll(DIRTY);
            DIRTY.clear();
            return out;
        }
    }

    /** Test helper: pending dirty count. */
    static int pendingCount() {
        synchronized (LOCK) {
            return DIRTY.size();
        }
    }

    /** Test helper: clear without sampling. */
    static void clearForTests() {
        synchronized (LOCK) {
            DIRTY.clear();
        }
    }
}
