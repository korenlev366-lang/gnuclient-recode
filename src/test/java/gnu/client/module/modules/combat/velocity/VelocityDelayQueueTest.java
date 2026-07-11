package gnu.client.module.modules.combat.velocity;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VelocityDelayQueueTest {

    private VelocityDelayQueue queue;

    @Before
    public void setUp() {
        queue = new VelocityDelayQueue();
    }

    @Test
    public void offerThenClearEmptiesQueueAndResetsDelayState() {
        queue.offer(new Object());
        queue.startDelay(10L);
        assertTrue(queue.isDelaying());

        queue.clear();

        assertFalse(queue.isDelaying());
        assertEquals(0L, queue.ticksHeld(20L));
    }

    @Test
    public void startDelayAndTicksHeld() {
        queue.startDelay(5L);
        assertTrue(queue.isDelaying());
        assertEquals(0L, queue.ticksHeld(5L));
        assertEquals(3L, queue.ticksHeld(8L));
    }

    @Test
    public void stopDelayAndFlushClearsDelayingWithoutPlayer() {
        queue.offer(new Object());
        queue.startDelay(1L);
        assertTrue(queue.isDelaying());

        queue.stopDelayAndFlush();

        assertFalse(queue.isDelaying());
        assertEquals(0L, queue.ticksHeld(100L));
    }

    @Test
    public void ticksHeldZeroWhenNotDelaying() {
        assertFalse(queue.isDelaying());
        assertEquals(0L, queue.ticksHeld(99L));
    }
}
