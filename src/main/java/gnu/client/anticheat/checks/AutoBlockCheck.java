package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import java.util.HashMap;
import java.util.Map;

public final class AutoBlockCheck {
    private final Map<String, Long> guardingTicks = new HashMap<String, Long>();
    private final Map<String, Long> lastBlockStart = new HashMap<String, Long>();
    private final Map<String, CheckBuffer> attackWhileBlockingBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> sprintBlockBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> rapidToggleBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> impossibleBlockBuffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null)
            return;
        String name = player.getName();

        ItemStack heldItem = player.getHeldItem();
        boolean holdingSword = heldItem != null && heldItem.getItem() instanceof ItemSword;
        boolean blocking = player.isBlocking();
        boolean guarding = holdingSword && blocking;
        boolean attacking = data.startedSwinging();
        double horizontalSpeed = data.horizontalDelta;

        CheckBuffer attackBuffer = buffer(attackWhileBlockingBuffers, name);
        CheckBuffer sprintBuffer = buffer(sprintBlockBuffers, name);
        CheckBuffer rapidBuffer = buffer(rapidToggleBuffers, name);
        CheckBuffer impossibleBuffer = buffer(impossibleBlockBuffers, name);

        if (!holdingSword && blocking) {
            if (impossibleBuffer.flag(1.5, CheckRules.AB_IMPOSSIBLE_THRESHOLD)) {
                context.receiveSignal(name, "AutoBlock");
                impossibleBuffer.reset();
            }
            return;
        }

        if (guarding) {
            if (!guardingTicks.containsKey(name)) {
                long lastStart = lastBlockStart.containsKey(name) ? lastBlockStart.get(name) : -100L;
                if (currentTick - lastStart <= 3L) {
                    if (rapidBuffer.flag(1.1, CheckRules.AB_RAPID_THRESHOLD)) {
                        context.receiveSignal(name, "AutoBlock");
                        rapidBuffer.reset();
                    }
                }
                guardingTicks.put(name, currentTick);
                lastBlockStart.put(name, currentTick);
            }

            long ticksGuarded = currentTick - guardingTicks.get(name);
            // Require sustained guard before scoring attack-while-block (legit right-click noise).
            if (attacking && ticksGuarded > 2L) {
                if (attackBuffer.flag(1.25, CheckRules.AB_ATTACK_THRESHOLD)) {
                    context.receiveSignal(name, "AutoBlock");
                    attackBuffer.reset();
                }
            } else {
                attackBuffer.decay(0.35);
            }

            if (player.isSprinting() && horizontalSpeed > CheckRules.AB_SPRINT_SPEED && ticksGuarded > 4L) {
                if (sprintBuffer.flag(1.1, CheckRules.AB_SPRINT_THRESHOLD)) {
                    context.receiveSignal(name, "AutoBlock");
                    sprintBuffer.reset();
                }
            } else {
                sprintBuffer.decay(0.3);
            }
        } else {
            guardingTicks.remove(name);
            attackBuffer.decay(0.45);
            sprintBuffer.decay(0.35);
            rapidBuffer.decay(0.2);
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
        guardingTicks.clear();
        lastBlockStart.clear();
        attackWhileBlockingBuffers.clear();
        sprintBlockBuffers.clear();
        rapidToggleBuffers.clear();
        impossibleBlockBuffers.clear();
    }
}
