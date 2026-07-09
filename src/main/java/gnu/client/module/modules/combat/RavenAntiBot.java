package gnu.client.module.modules.combat;

import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;

/**
 * Raven-bS / Raven-XD {@code AntiBot.isBot} heuristics.
 *
 * <p>Order matches raven-bS: module gate → dead/empty name → <b>tablist</b> →
 * red-name HP → {@code maxHurtTime == 0} NPC heuristics. Tablist must run
 * before the maxHurtTime early-out or Watchdog-style bots that have been hit
 * are never filtered.
 */
public final class RavenAntiBot {

    private RavenAntiBot() {}

    static void onTickCleanup() {
        // no-op (join delay removed)
    }

    public static boolean isBot(Object entity) {
        return entity instanceof Entity && isBot((Entity) entity);
    }

    public static boolean isBot(Entity entity) {
        // Raven: if (!ModuleManager.antiBot.isEnabled()) return false;
        if (!AntiBotModule.isActive())
            return false;

        if (entity == null)
            return true;
        if (entity == Mc.player())
            return false;
        if (!(entity instanceof EntityPlayer))
            return true;

        EntityPlayer player = (EntityPlayer) entity;
        String name = player.getName();

        if (player.isDead)
            return true;
        if (name == null || name.isEmpty())
            return true;

        // Tablist before maxHurtTime (raven-bS order) — bots often have maxHurtTime > 0.
        if (AntiBotModule.isTablistCheckEnabled()) {
            java.util.Set<String> tablist = Mc.getTablistNames();
            if (!tablist.isEmpty() && !tablist.contains(name))
                return true;
        }

        float health = player.getHealth();
        if (health != 20.0f && name.startsWith("\u00a7c"))
            return true;

        if (player.maxHurtTime == 0) {
            String unformatted = displayNameUnformatted(player);
            if (health == 20.0f) {
                if (unformatted.length() == 10
                        && (unformatted.isEmpty() || unformatted.charAt(0) != '\u00a7'))
                    return true;
                if (unformatted.length() == 12 && player.isPlayerSleeping()
                        && !unformatted.isEmpty() && unformatted.charAt(0) == '\u00a7')
                    return true;
                if (unformatted.length() >= 7 && unformatted.charAt(2) == '['
                        && unformatted.charAt(3) == 'N' && unformatted.charAt(6) == ']')
                    return true;
                if (name.contains(" "))
                    return true;
            } else if (player.isInvisible()) {
                if (unformatted.length() >= 3 && unformatted.charAt(0) == '\u00a7'
                        && unformatted.charAt(1) == 'c')
                    return true;
            }
        }

        return false;
    }

    private static String displayNameUnformatted(EntityPlayer entity) {
        IChatComponent displayName = entity.getDisplayName();
        if (displayName == null)
            return "";
        return displayName.getUnformattedText();
    }
}
