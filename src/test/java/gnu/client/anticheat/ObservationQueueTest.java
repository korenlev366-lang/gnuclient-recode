package gnu.client.anticheat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ObservationQueueTest {

    @Test
    public void swing_dedupesSameEntity() {
        ObservationQueue q = new ObservationQueue();
        q.offerSwing(5);
        q.offerSwing(5);
        q.offerSwing(7);
        List<Integer> swings = q.pollSwings();
        assertEquals(2, swings.size());
        assertTrue(swings.contains(5));
        assertTrue(swings.contains(7));
    }

    @Test
    public void velocity_preservesOrder() {
        ObservationQueue q = new ObservationQueue();
        q.offerVelocity(1, 10, 20, 30);
        q.offerVelocity(2, 0, 0, 0);
        List<ObservationQueue.VelocityObs> vels = q.pollVelocities();
        assertEquals(2, vels.size());
        assertEquals(1, vels.get(0).entityId);
        assertEquals(20, vels.get(0).my);
        assertEquals(2, vels.get(1).entityId);
    }

    @Test
    public void clear_emptiesQueues() {
        ObservationQueue q = new ObservationQueue();
        q.offerSwing(1);
        q.offerVelocity(1, 0, 0, 0);
        q.clear();
        assertTrue(q.pollSwings().isEmpty());
        assertTrue(q.pollVelocities().isEmpty());
    }
}
