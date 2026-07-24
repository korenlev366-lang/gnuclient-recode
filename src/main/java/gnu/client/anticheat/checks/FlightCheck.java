package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;

import java.util.HashMap;
import java.util.Map;

public final class FlightCheck {
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();
    private final Map<String, Integer> airAscend = new HashMap<String, Integer>();

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null)
            return;
        String name = player.getName();
        CheckBuffer buffer = buffer(name);

        if (CombatContext.isMovementEnvironmentExempt(player) || data.recentlyTeleported()
                || data.recentlyHurt() || data.observedFallDistance > 2.5F
                || player.capabilities.allowFlying) {
            airAscend.remove(name);
            buffer.decay(0.6);
            return;
        }

        if (data.onGround || data.airTicks < 14) {
            airAscend.remove(name);
            buffer.decay(0.4);
            return;
        }

        boolean ascending = data.deltaY > 0.08;
        boolean hovering = Math.abs(data.deltaY) < 0.03 && data.airTicks > 28;
        int ascend = airAscend.containsKey(name) ? airAscend.get(name) : 0;
        if (ascending)
            ascend++;
        else if (!hovering)
            ascend = Math.max(0, ascend - 1);
        airAscend.put(name, ascend);

        double maxUp = CheckRules.FLIGHT_MAX_UP + (player.isPotionActive(Potion.jump) ? 0.28 : 0.0);
        if (data.deltaY > maxUp && ascend > CheckRules.FLIGHT_MIN_ASCEND) {
            if (buffer.flag(1.4, CheckRules.FLIGHT_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "Flight");
                buffer.reset();
                airAscend.remove(name);
            }
        } else if (hovering && data.horizontalDelta > 0.15) {
            if (buffer.flag(1.15, CheckRules.FLIGHT_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "Flight");
                buffer.reset();
            }
        } else {
            buffer.decay(0.3);
        }
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
        buffers.clear();
        airAscend.clear();
    }
}
