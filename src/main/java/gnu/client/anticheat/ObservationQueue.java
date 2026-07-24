package gnu.client.anticheat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe inbound packet observations drained at the start of each AC tick
 * (prevents packet/tick races where a swing arrives after {@code update()}).
 */
public final class ObservationQueue {

    public static final class VelocityObs {
        public final int entityId;
        public final int mx;
        public final int my;
        public final int mz;

        public VelocityObs(int entityId, int mx, int my, int mz) {
            this.entityId = entityId;
            this.mx = mx;
            this.my = my;
            this.mz = mz;
        }
    }

    private final ConcurrentLinkedQueue<Integer> swings = new ConcurrentLinkedQueue<Integer>();
    private final ConcurrentLinkedQueue<VelocityObs> velocities = new ConcurrentLinkedQueue<VelocityObs>();
    private final Map<Integer, Boolean> swingSeen = new ConcurrentHashMap<Integer, Boolean>();

    public void offerSwing(int entityId) {
        if (swingSeen.putIfAbsent(entityId, Boolean.TRUE) == null)
            swings.offer(entityId);
    }

    public void offerVelocity(int entityId, int mx, int my, int mz) {
        velocities.offer(new VelocityObs(entityId, mx, my, mz));
    }

    public List<Integer> pollSwings() {
        List<Integer> out = new ArrayList<Integer>();
        Integer id;
        while ((id = swings.poll()) != null) {
            swingSeen.remove(id);
            out.add(id);
        }
        return out;
    }

    public List<VelocityObs> pollVelocities() {
        List<VelocityObs> out = new ArrayList<VelocityObs>();
        VelocityObs obs;
        while ((obs = velocities.poll()) != null)
            out.add(obs);
        return out;
    }

    public void clear() {
        swings.clear();
        velocities.clear();
        swingSeen.clear();
    }
}
