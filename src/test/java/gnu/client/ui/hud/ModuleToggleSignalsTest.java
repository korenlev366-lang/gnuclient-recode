package gnu.client.ui.hud;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.modules.visual.HudModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Final-state coalescing: dirty drain → sample isEnabled → baseline diff.
 * No Minecraft / GL.
 */
public class ModuleToggleSignalsTest {

    @Before
    @After
    public void clearSignals() {
        ModuleToggleSignals.clearForTests();
    }

    @Test
    public void markIsIdentityOnlyAndDrainClears() {
        StubModule a = new StubModule("A");
        StubModule b = new StubModule("B");
        ModuleToggleSignals.mark(a);
        ModuleToggleSignals.mark(a);
        ModuleToggleSignals.mark(b);
        assertEquals(2, ModuleToggleSignals.pendingCount());

        Set<Module> drained = ModuleToggleSignals.drain();
        assertEquals(2, drained.size());
        assertTrue(drained.contains(a));
        assertTrue(drained.contains(b));
        assertEquals(0, ModuleToggleSignals.pendingCount());
        assertTrue(ModuleToggleSignals.drain().isEmpty());
    }

    @Test
    public void rapidToggleCoalescesToFinalStateOnly() {
        StubModule m = new StubModule("FreeLook");
        Map<Module, Boolean> baselines = new IdentityHashMap<Module, Boolean>();
        HudRenderer.seedBaselines(Collections.<Module>singletonList(m), baselines);
        NotificationQueue queue = new NotificationQueue();

        // Simulate recursive enable→disable before drain (FreeLook pattern)
        m.forceEnabled(true);
        ModuleToggleSignals.mark(m);
        m.forceEnabled(false);
        ModuleToggleSignals.mark(m);

        Set<Module> drained = ModuleToggleSignals.drain();
        int pushed = HudRenderer.applyFinalStateDeltas(drained, baselines, queue, true);

        // Started disabled (seed), ended disabled → no toast
        assertEquals(0, pushed);
        assertEquals(0, queue.liveCount());
        assertEquals(Boolean.FALSE, baselines.get(m));
    }

    @Test
    public void realDeltaEnqueuesFinalSampledState() {
        StubModule m = new StubModule("Aura");
        Map<Module, Boolean> baselines = new IdentityHashMap<Module, Boolean>();
        HudRenderer.seedBaselines(Collections.<Module>singletonList(m), baselines);
        NotificationQueue queue = new NotificationQueue();

        m.forceEnabled(true);
        ModuleToggleSignals.mark(m);
        // Stale "disabled" would be wrong — sample after drain
        m.forceEnabled(true);

        int pushed = HudRenderer.applyFinalStateDeltas(
                ModuleToggleSignals.drain(), baselines, queue, true);
        assertEquals(1, pushed);
        assertEquals(1, queue.liveCount());
        assertTrue(queue.liveSnapshot().get(0).enabled);
        assertEquals("Aura", queue.liveSnapshot().get(0).moduleName);
    }

    @Test
    public void firstSeenBaselineSeedsSilentlyWithoutEnqueue() {
        StubModule m = new StubModule("NewScript");
        m.forceEnabled(true);
        Map<Module, Boolean> baselines = new IdentityHashMap<Module, Boolean>();
        NotificationQueue queue = new NotificationQueue();

        ModuleToggleSignals.mark(m);
        int pushed = HudRenderer.applyFinalStateDeltas(
                ModuleToggleSignals.drain(), baselines, queue, true);

        assertEquals(0, pushed);
        assertEquals(0, queue.liveCount());
        assertEquals(Boolean.TRUE, baselines.get(m));
    }

    @Test
    public void silentReseedProducesZeroToasts() {
        StubModule m = new StubModule("Speed");
        m.forceEnabled(true);
        Map<Module, Boolean> baselines = new IdentityHashMap<Module, Boolean>();
        NotificationQueue queue = new NotificationQueue();

        ModuleToggleSignals.mark(m);
        HudRenderer.seedBaselines(Collections.<Module>singletonList(m), baselines);
        ModuleToggleSignals.drain();

        int pushed = HudRenderer.applyFinalStateDeltas(
                Collections.<Module>emptySet(), baselines, queue, true);
        assertEquals(0, pushed);
        assertEquals(0, queue.liveCount());
    }

    @Test
    public void sanitizeSuffixesStripsAndCaps() {
        assertEquals("", HudRenderer.Pure.sanitizeSuffixes(null));
        assertEquals("", HudRenderer.Pure.sanitizeSuffixes(new String[0]));
        assertEquals("Switch", HudRenderer.Pure.sanitizeSuffixes(new String[] {"  Switch\n" }));
        String longPart = "abcdefghijklmnopqrstuvwxyz";
        String cleaned = HudRenderer.Pure.sanitizeSuffixes(new String[] { longPart, "two", "three" });
        assertEquals(24 + 1 + 3, cleaned.length()); // 24 + space + "two"
        assertTrue(cleaned.startsWith("abcdefghijklmnopqrstuvwx"));
        assertTrue(cleaned.endsWith("two"));
    }

    @Test
    public void arrayEligibilityRejectsHudAndSettings() {
        HudModule hud = new HudModule();
        assertFalse(HudRenderer.Pure.isArrayEligible(hud));
        assertFalse(HudRenderer.Pure.isArrayEligible(new StubModule("ClickGUI", Category.SETTINGS)));
        assertTrue(HudRenderer.Pure.isArrayEligible(new StubModule("Aura", Category.COMBAT)));
    }

    @Test
    public void arraySortWidthDescThenNameAsc() {
        assertTrue(HudRenderer.Pure.compareArrayOrder(100f, "B", 50f, "A") < 0);
        assertTrue(HudRenderer.Pure.compareArrayOrder(50f, "A", 100f, "B") > 0);
        assertTrue(HudRenderer.Pure.compareArrayOrder(80f, "Alpha", 80f, "Beta") < 0);
        assertTrue(HudRenderer.Pure.compareArrayOrder(80f, "beta", 80f, "Alpha") > 0);

        List<String[]> rows = new ArrayList<String[]>();
        rows.add(new String[] { "40", "Zebra" });
        rows.add(new String[] { "90", "Beta" });
        rows.add(new String[] { "90", "Alpha" });
        rows.add(new String[] { "10", "Tiny" });
        Collections.sort(rows, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return HudRenderer.Pure.compareArrayOrder(
                        Float.parseFloat(a[0]), a[1], Float.parseFloat(b[0]), b[1]);
            }
        });
        assertEquals("Alpha", rows.get(0)[1]);
        assertEquals("Beta", rows.get(1)[1]);
        assertEquals("Zebra", rows.get(2)[1]);
        assertEquals("Tiny", rows.get(3)[1]);
    }

    /** Minimal Module stub — bypasses HudModule/config side effects. */
    private static class StubModule extends Module {
        private boolean on;

        StubModule(String name) {
            this(name, Category.MISC);
        }

        StubModule(String name, Category category) {
            super(name, "test", category);
        }

        void forceEnabled(boolean enabled) {
            on = enabled;
        }

        @Override
        public boolean isEnabled() {
            return on;
        }

        @Override
        public void setEnabled(boolean enabled) {
            on = enabled;
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }
    }
}
