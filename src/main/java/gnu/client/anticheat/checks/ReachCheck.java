package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckGeometry;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/** Reach — combat-only (aim + no closer block); hit-correlation secondary. */
public final class ReachCheck {
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
        if (data == null || world == null || CombatContext.isInvalidSubject(player))
            return;
        if (data.recentlyTeleported() || !data.startedSwinging())
            return;
        if (CombatContext.isNonCombatSwing(player, data))
            return;

        EntityPlayer target = bestAimedTarget(player, world, data);
        if (target == null)
            return;
        if (CheckGeometry.isLookingAtBlockCloserThan(player, world, target))
            return;

        double dist = CheckGeometry.distanceToBox(CheckGeometry.eyes(player), CheckGeometry.combatBox(target));
        flagIfOver(data, player.getName(), dist, context, 0.0, CheckRules.REACH_SWING_BUFFER_THRESHOLD);
    }

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
            if (data.recentlyTeleported())
                continue;
            if (!data.recentlySwung() && !data.startedSwinging())
                continue;
            if (CombatContext.isNonCombatSwing(attacker, data))
                continue;
            if (attacker.getDistanceSqToEntity(victim) > CheckRules.MAX_COMBAT_RANGE * CheckRules.MAX_COMBAT_RANGE)
                continue;
            if (!CheckGeometry.isAimingAt(data, attacker, victim,
                    CheckRules.AIM_YAW_HIT_CORRELATE, CheckRules.AIM_PITCH_HIT_CORRELATE))
                continue;
            if (CheckGeometry.isLookingAtBlockCloserThan(attacker, world, victim))
                continue;
            String name = attacker.getName();
            double dist = CheckGeometry.distanceToBox(CheckGeometry.eyes(attacker), CheckGeometry.combatBox(victim));
            flagIfOver(data, name, dist, context, 0.1, CheckRules.REACH_HIT_BUFFER_THRESHOLD);
        }
    }

    private void flagIfOver(PlayerCheckData data, String name, double dist,
                            ClientAntiCheatContext context, double extraSlack, double threshold) {
        if (name == null || dist <= 0.0 || dist > CheckRules.MAX_COMBAT_RANGE)
            return;
        CheckBuffer buffer = buffer(name);
        double movementTolerance = Math.min(CheckRules.REACH_MOVE_TOLERANCE_CAP,
                data.horizontalDelta + data.lastHorizontalDelta);
        double allowed = CheckRules.REACH_BASE + movementTolerance
                + (data.recentlyHurt() ? CheckRules.REACH_HURT_BONUS : 0.0) + extraSlack;
        if (dist > allowed) {
            double over = dist - allowed;
            if (buffer.flag(1.0 + Math.min(2.0, over * 2.0), threshold)) {
                context.receiveSignal(name, "Reach");
                buffer.reset();
            }
        } else {
            buffer.decay(0.5);
        }
    }

    private static EntityPlayer bestAimedTarget(EntityPlayer player, World world, PlayerCheckData data) {
        EntityPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        double maxSq = CheckRules.MAX_COMBAT_RANGE * CheckRules.MAX_COMBAT_RANGE;
        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayer))
                continue;
            EntityPlayer target = (EntityPlayer) obj;
            if (target == player || CombatContext.isInvalidSubject(target))
                continue;
            double distSq = player.getDistanceSqToEntity(target);
            if (distSq > maxSq)
                continue;
            if (!CheckGeometry.isAimingAt(data, player, target,
                    CheckRules.AIM_YAW_COMBAT, CheckRules.AIM_PITCH_COMBAT))
                continue;
            if (distSq < bestScore) {
                bestScore = distSq;
                best = target;
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
        buffers.clear();
    }
}
