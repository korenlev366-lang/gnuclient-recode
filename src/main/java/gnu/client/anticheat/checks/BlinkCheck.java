package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Map;

public final class BlinkCheck {
    private final Map<String, Integer> frozenTicks = new HashMap<String, Integer>();
    private final Map<String, CheckBuffer> burstBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> pulseBuffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null)
            return;
        String name = player.getName();
        CheckBuffer burstBuffer = buffer(burstBuffers, name);
        CheckBuffer pulseBuffer = buffer(pulseBuffers, name);

        if (CombatContext.isMovementEnvironmentExempt(player) || data.recentlyTeleported()
                || player.ticksExisted < 40 || data.recentlyHurt()) {
            frozenTicks.remove(name);
            burstBuffer.decay(0.5);
            pulseBuffer.decay(0.5);
            return;
        }

        boolean frozen = data.totalDelta < 0.002 && data.yawDelta < 0.03F && data.pitchDelta < 0.03F;
        if (frozen) {
            Integer prev = frozenTicks.get(name);
            frozenTicks.put(name, (prev == null ? 0 : prev) + 1);
            burstBuffer.decay(0.1);
            return;
        }

        int frozenBefore = frozenTicks.containsKey(name) ? frozenTicks.get(name) : 0;
        frozenTicks.remove(name);
        boolean catchUpBurst = frozenBefore >= CheckRules.BLINK_MIN_FROZEN
                && data.totalDelta > CheckRules.BLINK_BURST_MIN
                && data.totalDelta < 8.0;
        boolean pulseMove = data.totalDelta > CheckRules.BLINK_PULSE_MIN
                && data.totalDelta < 8.0
                && data.horizontalDelta > 0.8;

        if (catchUpBurst) {
            if (burstBuffer.flag(1.5 + Math.min(1.0, frozenBefore / 20.0), CheckRules.BLINK_BURST_THRESHOLD)) {
                context.receiveSignal(name, "Blink");
                burstBuffer.reset();
                pulseBuffer.reset();
            }
        } else {
            burstBuffer.decay(0.25);
        }

        if (pulseMove && !player.isCollidedHorizontally && data.airTicks < 8) {
            if (pulseBuffer.flag(1.0, CheckRules.BLINK_PULSE_THRESHOLD)) {
                context.receiveSignal(name, "Blink");
                pulseBuffer.reset();
            }
        } else {
            pulseBuffer.decay(0.2);
        }
    }

    private static CheckBuffer buffer(Map<String, CheckBuffer> map, String name) {
        CheckBuffer buffer = map.get(name);
        if (buffer == null) {
            buffer = new CheckBuffer();
            map.put(name, buffer);
        }
        return buffer;
    }

    public void reset() {
        frozenTicks.clear();
        burstBuffers.clear();
        pulseBuffers.clear();
    }
}
