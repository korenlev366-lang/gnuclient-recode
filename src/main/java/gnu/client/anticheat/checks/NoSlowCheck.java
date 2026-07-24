package gnu.client.anticheat.checks;

import gnu.client.anticheat.CheckBuffer;
import gnu.client.anticheat.CheckRules;
import gnu.client.anticheat.ClientAntiCheatContext;
import gnu.client.anticheat.CombatContext;
import gnu.client.anticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;

import java.util.HashMap;
import java.util.Map;

/** Noslow — sword/bow/food/potion use only (never ItemBlock place swings). */
public final class NoSlowCheck {
    private final Map<String, CheckBuffer> sprintBuffers = new HashMap<String, CheckBuffer>();
    private final Map<String, CheckBuffer> speedBuffers = new HashMap<String, CheckBuffer>();

    public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
        if (CombatContext.isInvalidSubject(player) || data == null)
            return;
        String name = player.getName();

        ItemStack heldItem = player.getHeldItem();
        boolean usingSlowItem = isSlowItem(heldItem)
                && (player.isBlocking() || player.isEating() || player.isUsingItem());
        CheckBuffer sprintBuffer = buffer(sprintBuffers, name);
        CheckBuffer speedBuffer = buffer(speedBuffers, name);

        if (!usingSlowItem || isExempt(player, data)) {
            sprintBuffer.decay(0.5);
            speedBuffer.decay(0.5);
            return;
        }

        int ticksUsing = data.usingItemTicks;
        double horizontalSpeed = data.horizontalDelta;
        double expected = player.onGround ? CheckRules.NS_GROUND_EXPECTED : CheckRules.NS_AIR_EXPECTED;
        if (player.isPotionActive(Potion.moveSpeed))
            expected += CheckRules.NS_SPEED_POT_BONUS
                    * (player.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);

        if (ticksUsing > CheckRules.NS_MIN_USE_TICKS_SPRINT && player.isSprinting()) {
            if (sprintBuffer.flag(1.1, CheckRules.NS_SPRINT_THRESHOLD)) {
                context.receiveSignal(name, "Noslow");
                sprintBuffer.reset();
            }
        } else {
            sprintBuffer.decay(0.3);
        }

        if (ticksUsing > CheckRules.NS_MIN_USE_TICKS_SPEED && horizontalSpeed > expected) {
            double over = horizontalSpeed - expected;
            if (speedBuffer.flag(1.1 + Math.min(1.6, over * 5.5), CheckRules.NS_SPEED_THRESHOLD)) {
                context.receiveSignal(name, "Noslow");
                speedBuffer.reset();
            }
        } else {
            speedBuffer.decay(0.25);
        }
    }

    private static boolean isExempt(EntityPlayer player, PlayerCheckData data) {
        return CombatContext.isMovementEnvironmentExempt(player)
                || player.hurtTime > 0
                || player.hurtResistantTime > 10
                || player.isCollidedHorizontally
                || data.recentlyTeleported()
                || data.recentlyHurt();
    }

    /** Blocks intentionally excluded — placing is not noslow. */
    private static boolean isSlowItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null)
            return false;
        return stack.getItem() instanceof ItemSword
                || stack.getItem() instanceof ItemBow
                || stack.getItem() instanceof ItemFood
                || stack.getItem() instanceof ItemPotion;
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
        sprintBuffers.clear();
        speedBuffers.clear();
    }
}
