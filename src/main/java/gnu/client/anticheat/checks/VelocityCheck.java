package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckDataManager;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Map;

public final class VelocityCheck {
    private final Map<String, Integer> velocityWindows = new HashMap<String, Integer>();
    private final Map<String, CheckBuffer> horizontalBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> verticalBuffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null)
            return;
        String key = CheckDataManager.getPlayerKey(player);
        String name = player.getName();
        if (key == null)
            return;

        CheckBuffer horizontalBuffer = buffer(horizontalBuffers, key);
        CheckBuffer verticalBuffer = buffer(verticalBuffers, key);
        if (CombatContext.isMovementEnvironmentExempt(player) || data.recentlyTeleported()
                || CombatContext.isCreativeOrSpectator(player)) {
            resetKey(key);
            return;
        }

        // Tiny knockback (arrow tip / weak) — don't start a window.
        if (data.velocityPacketTicks == 0) {
            if (data.expectedVelH < 0.08 && data.expectedVelY < 0.08) {
                data.velocityPacketTicks = -1;
                return;
            }
            velocityWindows.put(key, 0);
            horizontalBuffer.reset();
            verticalBuffer.reset();
        } else if (data.justTookHit && data.velocityPacketTicks < 0) {
            velocityWindows.put(key, 0);
            horizontalBuffer.reset();
            verticalBuffer.reset();
        }

        if (!velocityWindows.containsKey(key)) {
            horizontalBuffer.decay(0.2);
            verticalBuffer.decay(0.2);
            return;
        }

        int ticks = velocityWindows.get(key) + 1;
        velocityWindows.put(key, ticks);
        if (ticks > 12) {
            velocityWindows.remove(key);
            if (data.velocityPacketTicks > 12)
                data.velocityPacketTicks = -1;
            return;
        }

        double expectedH = data.expectedVelH > 0.05 ? data.expectedVelH : 0.2;
        double minH = Math.max(0.02, expectedH * 0.15);
        boolean horizontalMissing = ticks >= 2 && ticks <= 7
                && data.horizontalDelta < minH
                && data.lastHorizontalDelta < minH * 1.6
                && !player.isCollidedHorizontally;

        boolean expectUp = data.expectedVelY > 0.1 || data.velocityPacketTicks < 0;
        boolean verticalMissing = expectUp && ticks >= 1 && ticks <= 5
                && data.deltaY <= 0.01
                && !data.onGround
                && data.airTicks <= 4;

        if (horizontalMissing) {
            if (horizontalBuffer.flag(1.15, CheckRules.VEL_H_THRESHOLD)) {
                context.receiveSignal(name, "Velocity");
                resetKey(key);
                data.velocityPacketTicks = -1;
                return;
            }
        } else {
            horizontalBuffer.decay(0.45);
        }

        if (verticalMissing) {
            if (verticalBuffer.flag(1.1, CheckRules.VEL_V_THRESHOLD)) {
                context.receiveSignal(name, "Velocity");
                resetKey(key);
                data.velocityPacketTicks = -1;
            }
        } else {
            verticalBuffer.decay(0.45);
        }
    }

    private void resetKey(String key) {
        velocityWindows.remove(key);
        horizontalBuffers.remove(key);
        verticalBuffers.remove(key);
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
        velocityWindows.clear();
        horizontalBuffers.clear();
        verticalBuffers.clear();
    }
}
