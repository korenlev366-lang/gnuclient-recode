package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckGeometry;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects Backtrack-style inbound lag abuse from a third-party view:
 * attacker lands a hit while far from the victim's current position,
 * was aiming poorly at the current box (old ghost position), and was
 * NOT freeze-bursting (that would be Lagrange/Blink instead).
 */
public final class BacktrackCheck {
    private final Map<String, Integer> recentStillTicks = new HashMap<String, Integer>();
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();

    public void tickTracker(EntityPlayer player, PlayerCheckData data) {
        if (player == null || data == null)
            return;
        String name = player.getName();
        if (name == null)
            return;
        boolean still = data.totalDelta < 0.01 && data.yawDelta < 0.1F;
        if (still) {
            Integer n = recentStillTicks.get(name);
            recentStillTicks.put(name, (n == null ? 0 : n) + 1);
        } else {
            recentStillTicks.put(name, 0);
        }
    }

    /**
     * Call when {@code victim} just took damage.
     */
    public void onVictimHit(EntityPlayer victim, World world,
                            Map<EntityPlayer, PlayerCheckData> snapshot,
                            ClientAntiCheatContext context) {
        if (victim == null || world == null || snapshot == null)
            return;

        for (Map.Entry<EntityPlayer, PlayerCheckData> entry : snapshot.entrySet()) {
            EntityPlayer attacker = entry.getKey();
            PlayerCheckData data = entry.getValue();
            if (attacker == victim || data == null || CombatContext.isInvalidSubject(attacker))
                continue;
            if (data.recentlyTeleported() || CombatContext.isNonCombatSwing(attacker, data))
                continue;
            if (!data.recentlySwung() && !data.startedSwinging())
                continue;

            double dist = CheckGeometry.distanceToBox(
                    CheckGeometry.eyes(attacker), CheckGeometry.combatBox(victim));
            if (dist < CheckRules.BACKTRACK_MIN_HIT_DISTANCE || dist > CheckRules.MAX_COMBAT_RANGE)
                continue;

            // Must look like they swung at an OLD position: high aim error to current victim.
            float yawErr = Math.abs(MathHelper.wrapAngleTo180_float(
                    CheckGeometry.yawTo(attacker, victim) - data.yaw));
            float pitchErr = Math.abs(CheckGeometry.pitchTo(attacker, victim) - data.pitch);
            if (yawErr < CheckRules.BACKTRACK_MIN_AIM_ERROR && pitchErr < CheckRules.BACKTRACK_MIN_AIM_ERROR)
                continue;

            // Continuous movement (not outbound blink) — inbound backtrack signature.
            Integer still = recentStillTicks.get(attacker.getName());
            if (still != null && still >= 4)
                continue;
            if (data.stillTicks >= 4)
                continue;

            // Smooth recent motion — not a teleport dump.
            if (data.totalDelta > 1.5)
                continue;

            String name = attacker.getName();
            CheckBuffer buffer = buffer(name);
            double over = dist - CheckRules.REACH_BASE;
            if (buffer.flag(1.2 + Math.min(2.0, over * 1.8), CheckRules.BACKTRACK_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "Backtrack");
                buffer.reset();
            }
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
        recentStillTicks.clear();
        buffers.clear();
    }
}
