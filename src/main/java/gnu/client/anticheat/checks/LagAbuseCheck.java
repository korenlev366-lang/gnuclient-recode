package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * General lag / ping-spike abuse: many short freezes in a sliding window
 * while near other players (FakeLag / ping spoof / spike patterns).
 */
public final class LagAbuseCheck {
    private final Map<String, Integer> freezeEvents = new HashMap<String, Integer>();
    private final Map<String, Long> windowStart = new HashMap<String, Long>();
    private final Map<String, Integer> streakFrozen = new HashMap<String, Integer>();
    private final Map<String, Boolean> wasFrozen = new HashMap<String, Boolean>();
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, World world, PlayerCheckData data, long currentTick,
                      ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null || world == null)
            return;
        String name = player.getName();
        CheckBuffer buffer = buffer(name);

        if (CombatContext.isMovementEnvironmentExempt(player) || data.recentlyTeleported()
                || player.ticksExisted < 40 || data.recentlyHurt()) {
            streakFrozen.remove(name);
            wasFrozen.put(name, Boolean.FALSE);
            buffer.decay(0.4);
            return;
        }

        boolean nearOthers = hasNearbyPlayer(player, world, 12.0);
        if (!nearOthers) {
            buffer.decay(0.35);
            return;
        }

        boolean frozen = data.totalDelta < 0.003 && data.yawDelta < 0.05F && data.pitchDelta < 0.05F;
        Boolean prev = wasFrozen.get(name);
        boolean was = prev != null && prev;

        if (frozen) {
            Integer streak = streakFrozen.get(name);
            streakFrozen.put(name, (streak == null ? 0 : streak) + 1);
            wasFrozen.put(name, Boolean.TRUE);
            return;
        }

        int streak = streakFrozen.containsKey(name) ? streakFrozen.get(name) : 0;
        streakFrozen.put(name, 0);
        wasFrozen.put(name, Boolean.FALSE);

        // Ended a short freeze (2–10 ticks) with a catch-up — count as lag event.
        if (was && streak >= CheckRules.LAGABUSE_MIN_FREEZE
                && streak <= CheckRules.LAGABUSE_MAX_FREEZE
                && data.totalDelta > CheckRules.LAGABUSE_BURST_MIN
                && data.totalDelta < 7.0) {
            noteEvent(name, currentTick);
        }

        Integer events = freezeEvents.get(name);
        if (events != null && events >= CheckRules.LAGABUSE_EVENT_COUNT) {
            if (buffer.flag(1.6, CheckRules.LAGABUSE_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "LagAbuse");
                buffer.reset();
            }
            freezeEvents.remove(name);
            windowStart.remove(name);
        } else {
            buffer.decay(0.25);
        }
    }

    private void noteEvent(String name, long currentTick) {
        Long start = windowStart.get(name);
        if (start == null || currentTick - start > CheckRules.LAGABUSE_WINDOW_TICKS) {
            windowStart.put(name, currentTick);
            freezeEvents.put(name, 1);
        } else {
            Integer n = freezeEvents.get(name);
            freezeEvents.put(name, (n == null ? 0 : n) + 1);
        }
    }

    private static boolean hasNearbyPlayer(EntityPlayer player, World world, double maxDist) {
        double maxSq = maxDist * maxDist;
        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayer))
                continue;
            EntityPlayer other = (EntityPlayer) obj;
            if (other == player || CombatContext.isInvalidSubject(other))
                continue;
            if (player.getDistanceSqToEntity(other) <= maxSq)
                return true;
        }
        return false;
    }

    private CheckBuffer buffer(String name) {
        CheckBuffer buffer = buffers.get(name);
        if (buffer == null) {
            buffer = new CheckBuffer();
            buffers.put(name, buffer);
        }
        return buffer;
    }

    public void reset() {
        freezeEvents.clear();
        windowStart.clear();
        streakFrozen.clear();
        wasFrozen.clear();
        buffers.clear();
    }
}
