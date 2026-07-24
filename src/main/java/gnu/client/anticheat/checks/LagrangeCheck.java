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
 * Detects Lagrange / LagRange-style outbound combat lag:
 * short freezes while closing on a target, then catch-up bursts
 * (often aligned with swings). More combat-scoped than generic Blink.
 */
public final class LagrangeCheck {
    private final Map<String, Integer> frozenTicks = new HashMap<String, Integer>();
    private final Map<String, Integer> pulseCount = new HashMap<String, Integer>();
    private final Map<String, Long> pulseWindowStart = new HashMap<String, Long>();
    private final Map<String, Double> lastCombatDist = new HashMap<String, Double>();
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, World world, PlayerCheckData data, long currentTick,
                      ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null || world == null)
            return;
        String name = player.getName();
        CheckBuffer buffer = buffer(name);

        if (CombatContext.isMovementEnvironmentExempt(player) || data.recentlyTeleported()
                || player.ticksExisted < 40) {
            clear(name);
            buffer.decay(0.5);
            return;
        }

        EntityPlayer target = nearestPlayer(player, world, 8.0);
        double combatDist = target == null ? Double.MAX_VALUE : Math.sqrt(player.getDistanceSqToEntity(target));
        boolean inCombatRange = target != null && combatDist <= 8.0;
        boolean closing = false;
        if (inCombatRange) {
            Double prev = lastCombatDist.get(name);
            if (prev != null && combatDist + 0.05 < prev)
                closing = true;
            lastCombatDist.put(name, combatDist);
        } else {
            lastCombatDist.remove(name);
        }

        boolean frozen = data.totalDelta < 0.003 && data.yawDelta < 0.05F && data.pitchDelta < 0.05F;
        if (frozen) {
            Integer prev = frozenTicks.get(name);
            frozenTicks.put(name, (prev == null ? 0 : prev) + 1);
            return;
        }

        int frozenBefore = frozenTicks.containsKey(name) ? frozenTicks.get(name) : 0;
        frozenTicks.remove(name);

        // Short combat freezes (Lagrange max-age ~200ms ≈ 4 ticks) then burst.
        boolean shortCombatBurst = inCombatRange
                && frozenBefore >= CheckRules.LAGRANGE_MIN_FROZEN
                && frozenBefore <= CheckRules.LAGRANGE_MAX_FROZEN
                && data.totalDelta > CheckRules.LAGRANGE_BURST_MIN
                && data.totalDelta < 6.0
                && (closing || data.recentlySwung() || data.startedSwinging());

        boolean attackAlignedBurst = inCombatRange
                && frozenBefore >= 3
                && data.totalDelta > 0.45
                && (data.startedSwinging() || data.recentlySwung());

        if (shortCombatBurst || attackAlignedBurst) {
            notePulse(name, currentTick);
            double add = 1.25 + Math.min(1.5, frozenBefore / 8.0);
            if (buffer.flag(add, CheckRules.LAGRANGE_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "Lagrange");
                buffer.reset();
                pulseCount.remove(name);
            }
        } else {
            buffer.decay(0.3);
        }

        // Repeated freeze→burst pulses while closing (classic LagRange cadence).
        Integer pulses = pulseCount.get(name);
        if (pulses != null && pulses >= CheckRules.LAGRANGE_PULSE_COUNT) {
            if (buffer.flag(1.5, CheckRules.LAGRANGE_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "Lagrange");
                buffer.reset();
            }
            pulseCount.remove(name);
            pulseWindowStart.remove(name);
        }
    }

    private void notePulse(String name, long currentTick) {
        Long start = pulseWindowStart.get(name);
        if (start == null || currentTick - start > CheckRules.LAGRANGE_PULSE_WINDOW_TICKS) {
            pulseWindowStart.put(name, currentTick);
            pulseCount.put(name, 1);
        } else {
            Integer n = pulseCount.get(name);
            pulseCount.put(name, (n == null ? 0 : n) + 1);
        }
    }

    private void clear(String name) {
        frozenTicks.remove(name);
        pulseCount.remove(name);
        pulseWindowStart.remove(name);
        lastCombatDist.remove(name);
    }

    private static EntityPlayer nearestPlayer(EntityPlayer player, World world, double maxDist) {
        EntityPlayer best = null;
        double bestSq = maxDist * maxDist;
        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayer))
                continue;
            EntityPlayer other = (EntityPlayer) obj;
            if (other == player || CombatContext.isInvalidSubject(other))
                continue;
            double d = player.getDistanceSqToEntity(other);
            if (d < bestSq) {
                bestSq = d;
                best = other;
            }
        }
        return best;
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
        frozenTicks.clear();
        pulseCount.clear();
        pulseWindowStart.clear();
        lastCombatDist.clear();
        buffers.clear();
    }
}
