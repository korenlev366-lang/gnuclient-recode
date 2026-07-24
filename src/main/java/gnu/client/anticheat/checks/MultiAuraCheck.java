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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MultiAuraCheck {
    private final Map<String, Set<String>> recentTargets = new HashMap<String, Set<String>>();
    private final Map<String, Long> windowStart = new HashMap<String, Long>();
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, World world, PlayerCheckData data, long currentTick,
                      ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null || world == null)
            return;
        if (data.recentlyTeleported() || !data.startedSwinging())
            return;
        if (CombatContext.isNonCombatSwing(player, data))
            return;
        String name = player.getName();

        Long start = windowStart.get(name);
        if (start == null || currentTick - start > 12L) {
            windowStart.put(name, currentTick);
            recentTargets.put(name, new HashSet<String>());
        }
        Set<String> targets = recentTargets.get(name);
        if (targets == null) {
            targets = new HashSet<String>();
            recentTargets.put(name, targets);
        }

        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayer))
                continue;
            EntityPlayer other = (EntityPlayer) obj;
            if (other == player || CombatContext.isInvalidSubject(other))
                continue;
            if (player.getDistanceSqToEntity(other) > 36.0)
                continue;
            float yawErr = Math.abs(MathHelper.wrapAngleTo180_float(
                    CheckGeometry.yawTo(player, other) - data.yaw));
            if (yawErr < CheckRules.MA_AIM_YAW)
                targets.add(other.getName());
        }

        CheckBuffer buffer = buffer(name);
        if (targets.size() >= CheckRules.MA_MIN_TARGETS) {
            if (buffer.flag(1.4, CheckRules.MA_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "MultiAura");
                buffer.reset();
                targets.clear();
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
        recentTargets.clear();
        windowStart.clear();
        buffers.clear();
    }
}
