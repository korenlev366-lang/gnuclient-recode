package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public final class GroundSpoofCheck {
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null || world == null)
            return;
        String name = player.getName();
        CheckBuffer buffer = buffer(name);

        if (CombatContext.isMovementEnvironmentExempt(player) || data.recentlyTeleported()
                || data.recentlyHurt()) {
            buffer.decay(0.5);
            return;
        }

        if (!data.onGround) {
            buffer.decay(0.4);
            return;
        }

        AxisAlignedBB below = player.getEntityBoundingBox().offset(0.0, -0.08, 0.0);
        boolean supported = !world.getCollidingBoundingBoxes(player, below).isEmpty();
        // Also treat slim support (half slabs / edge) via slightly deeper probe.
        if (!supported) {
            AxisAlignedBB deeper = player.getEntityBoundingBox().offset(0.0, -0.16, 0.0);
            supported = !world.getCollidingBoundingBoxes(player, deeper).isEmpty();
        }

        if (!supported && data.deltaY >= -0.05 && data.observedFallDistance < 0.15F && data.airTicks == 0) {
            if (buffer.flag(1.3, CheckRules.GROUND_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "Ground");
                buffer.reset();
            }
        } else {
            buffer.decay(0.4);
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
    }
}
