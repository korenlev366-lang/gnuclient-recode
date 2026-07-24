package gnu.client.anticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/** Shared filters so mining/building / invalid entities don't look like PvP cheats. */
public final class CombatContext {
    private CombatContext() {}

    public static boolean isInvalidSubject(EntityPlayer player) {
        return player == null
                || player.isDead
                || player.getName() == null
                || player.getName().isEmpty()
                || player.ticksExisted < 20;
    }

    public static boolean isCreativeOrSpectator(EntityPlayer player) {
        return player != null && (player.capabilities.isFlying || player.capabilities.disableDamage);
    }

    public static boolean isMovementEnvironmentExempt(EntityPlayer player) {
        return player == null
                || player.isInWater()
                || player.isInLava()
                || player.isOnLadder()
                || player.isRiding()
                || player.capabilities.isFlying;
    }

    public static boolean isNonCombatSwing(EntityPlayer player, PlayerCheckData data) {
        if (player == null || data == null)
            return true;
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() != null) {
            Item item = held.getItem();
            if (item instanceof ItemPickaxe || item instanceof ItemSpade || item instanceof ItemBlock)
                return true;
            if (item instanceof ItemAxe && data.pitch > CheckRules.NON_COMBAT_AXE_PITCH)
                return true;
            if (!(item instanceof ItemSword) && !(item instanceof ItemAxe)
                    && data.pitch > CheckRules.NON_COMBAT_OTHER_PITCH)
                return true;
        }
        return false;
    }

    public static boolean isSword(EntityPlayer player) {
        ItemStack held = player == null ? null : player.getHeldItem();
        return held != null && held.getItem() instanceof ItemSword;
    }
}
