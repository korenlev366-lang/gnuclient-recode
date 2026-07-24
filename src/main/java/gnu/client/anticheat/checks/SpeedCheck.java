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

public final class SpeedCheck {
    private final CheckDataManager dataManager;
    private final Map<String, CheckBuffer> buffers = new HashMap<String, CheckBuffer>();

    public SpeedCheck(CheckDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null)
            return;
        String name = player.getName();
        CheckBuffer buffer = buffer(name);
        if (dataManager.isMovementExempt(player, data) || data.recentlyHurt()
                || player.isCollidedHorizontally) {
            buffer.decay(0.55);
            return;
        }

        double limit = data.predictedHorizontalLimit(player) * CheckRules.SPEED_LIMIT_SLACK;
        if (data.horizontalDelta > limit && data.horizontalDelta < 8.0) {
            double over = data.horizontalDelta - limit;
            if (buffer.flag(1.1 + Math.min(2.0, over * 4.5), CheckRules.SPEED_BUFFER_THRESHOLD)) {
                context.receiveSignal(name, "Speed");
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
