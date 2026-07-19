package gnu.client.ui.hud;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pure-state notification queue tests — no Minecraft boot.
 */
public class NotificationQueueTest {

    private NotificationQueue queue;

    @Before
    public void setUp() {
        queue = new NotificationQueue();
    }

    @Test
    public void liveCapDropsOldestIntoExit() {
        long t0 = 1_000_000_000L;
        for (int i = 0; i < NotificationQueue.LIVE_CAP + 2; i++) {
            queue.enqueue("M" + i, true, t0 + i);
        }
        assertEquals(NotificationQueue.LIVE_CAP, queue.liveCount());
        assertEquals(2, queue.exitingCount());
        assertEquals("M2", queue.liveSnapshot().get(0).moduleName);
        assertEquals("M9", queue.liveSnapshot().get(NotificationQueue.LIVE_CAP - 1).moduleName);
    }

    @Test
    public void exitCapDropsOldestExiting() {
        long t0 = 1_000_000_000L;
        for (int i = 0; i < NotificationQueue.LIVE_CAP; i++) {
            queue.enqueue("L" + i, true, t0);
        }
        // Force all live into exit via advance past hold
        queue.advance(t0 + NotificationQueue.HOLD_NS);
        assertEquals(0, queue.liveCount());
        assertEquals(NotificationQueue.LIVE_CAP, queue.exitingCount());

        // Overflow exit by pushing more and advancing
        for (int i = 0; i < NotificationQueue.EXIT_CAP + 3; i++) {
            queue.enqueue("E" + i, false, t0 + NotificationQueue.HOLD_NS + i);
        }
        queue.advance(t0 + NotificationQueue.HOLD_NS + NotificationQueue.HOLD_NS);
        assertTrue(queue.exitingCount() <= NotificationQueue.EXIT_CAP);
    }

    @Test
    public void suppressBlocksIngress() {
        queue.setSuppress(true);
        queue.enqueue("X", true, System.nanoTime());
        assertEquals(0, queue.liveCount());
        queue.setSuppress(false);
        queue.enqueue("Y", true, System.nanoTime());
        assertEquals(1, queue.liveCount());
    }

    @Test
    public void clearAllResetsLiveAndExit() {
        long t0 = 1_000_000_000L;
        queue.enqueue("A", true, t0);
        queue.advance(t0 + NotificationQueue.HOLD_NS);
        assertTrue(queue.hasActive());
        queue.clearAll();
        assertFalse(queue.hasActive());
        assertEquals(0, queue.liveCount());
        assertEquals(0, queue.exitingCount());
    }

    @Test
    public void newestIsBottomOfStack() {
        long t0 = 1_000_000_000L;
        queue.enqueue("old", true, t0);
        queue.enqueue("new", false, t0 + 1);
        assertEquals("new", queue.bottomFirst().get(0).moduleName);
        assertEquals("old", queue.bottomFirst().get(1).moduleName);
    }

    @Test
    public void holdThenExitRemovesAfterExitWindow() {
        long t0 = 1_000_000_000L;
        queue.enqueue("A", true, t0);
        queue.advance(t0 + NotificationQueue.HOLD_NS - 1);
        assertEquals(1, queue.liveCount());
        queue.advance(t0 + NotificationQueue.HOLD_NS);
        assertEquals(0, queue.liveCount());
        assertEquals(1, queue.exitingCount());
        queue.advance(t0 + NotificationQueue.HOLD_NS + NotificationQueue.EXIT_NS);
        assertEquals(0, queue.exitingCount());
    }

    @Test
    public void bottomFirstReusesInstanceAndRefreshesContents() {
        long t0 = 1_000_000_000L;
        queue.enqueue("A", true, t0);
        List<NotificationQueue.Entry> first = queue.bottomFirst();
        // Re-calling without mutation returns the same cached list (no per-call alloc).
        assertSame(first, queue.bottomFirst());
        assertEquals(1, first.size());

        // A new enqueue refreshes the cached list's contents (same instance, updated size).
        queue.enqueue("B", false, t0 + 1);
        List<NotificationQueue.Entry> second = queue.bottomFirst();
        assertSame(first, second);
        assertEquals(2, second.size());
        assertEquals("B", second.get(0).moduleName);
    }
}
