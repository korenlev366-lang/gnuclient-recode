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

public final class KillAuraCheck {
    private final Map<String, Long> lastAttackTicks = new HashMap<String, Long>();
    private final Map<String, CheckBuffer> rateBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> aimBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> snapBuffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, World world, PlayerCheckData data, long currentTick,
                      ClientAntiCheatContext context) {
        String name = player == null ? null : player.getName();
        if (name == null || data == null || world == null || CombatContext.isInvalidSubject(player))
            return;
        if (data.recentlyTeleported())
            return;
        if (!data.startedSwinging()) {
            decay(name);
            return;
        }
        if (CombatContext.isNonCombatSwing(player, data)) {
            decay(name);
            return;
        }

        EntityPlayer target = nearestAimed(player, world, data);
        if (target == null)
            return;
        if (CheckGeometry.isLookingAtBlockCloserThan(player, world, target))
            return;

        CheckBuffer rateBuffer = buffer(rateBuffers, name);
        CheckBuffer aimBuffer = buffer(aimBuffers, name);
        CheckBuffer snapBuffer = buffer(snapBuffers, name);

        long lastAttack = lastAttackTicks.containsKey(name) ? lastAttackTicks.get(name) : currentTick - 20L;
        long delay = currentTick - lastAttack;
        lastAttackTicks.put(name, currentTick);
        if (delay > 0L && delay < 4L)
            rateBuffer.flag(CheckRules.KA_RATE_FLAG, 999.0);
        else
            rateBuffer.decay(0.3);

        float yawError = Math.abs(MathHelper.wrapAngleTo180_float(CheckGeometry.yawTo(player, target) - data.yaw));
        float pitchError = Math.abs(CheckGeometry.pitchTo(player, target) - data.pitch);
        if (yawError > CheckRules.KA_AIM_YAW_ERR || pitchError > CheckRules.KA_AIM_PITCH_ERR)
            aimBuffer.flag(CheckRules.KA_AIM_FLAG, 999.0);
        else
            aimBuffer.decay(0.4);

        if ((data.yawDelta > CheckRules.KA_SNAP_YAW_DELTA || data.yawAcceleration > CheckRules.KA_SNAP_YAW_ACCEL)
                && yawError < CheckRules.KA_SNAP_YAW_ERR_MAX)
            snapBuffer.flag(CheckRules.KA_SNAP_FLAG, 999.0);
        else
            snapBuffer.decay(0.3);

        if ((rateBuffer.get() > CheckRules.KA_RATE_NEED && aimBuffer.get() > CheckRules.KA_AIM_NEED)
                || (snapBuffer.get() > CheckRules.KA_SNAP_NEED && rateBuffer.get() > 2.0)
                || (snapBuffer.get() > CheckRules.KA_SNAP_NEED && aimBuffer.get() > 1.75)) {
            context.receiveSignal(name, "KillAura");
            rateBuffer.reset();
            aimBuffer.reset();
            snapBuffer.reset();
        }
    }

    private static EntityPlayer nearestAimed(EntityPlayer player, World world, PlayerCheckData data) {
        EntityPlayer nearest = null;
        double best = CheckRules.MAX_COMBAT_RANGE * CheckRules.MAX_COMBAT_RANGE;
        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayer))
                continue;
            EntityPlayer target = (EntityPlayer) obj;
            if (target == player || CombatContext.isInvalidSubject(target))
                continue;
            double distance = player.getDistanceSqToEntity(target);
            if (distance >= best)
                continue;
            if (!CheckGeometry.isAimingAt(data, player, target, 40.0F, 35.0F))
                continue;
            best = distance;
            nearest = target;
        }
        return nearest;
    }

    private void decay(String name) {
        CheckBuffer rate = rateBuffers.get(name);
        CheckBuffer aim = aimBuffers.get(name);
        CheckBuffer snap = snapBuffers.get(name);
        if (rate != null)
            rate.decay(0.15);
        if (aim != null)
            aim.decay(0.15);
        if (snap != null)
            snap.decay(0.15);
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
        lastAttackTicks.clear();
        rateBuffers.clear();
        aimBuffers.clear();
        snapBuffers.clear();
    }
}
