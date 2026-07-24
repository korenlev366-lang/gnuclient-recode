package gnu.client.anticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class CheckDataManager {
    private final Map<String, PlayerCheckData> data = new HashMap<String, PlayerCheckData>();

    public void update(World world) {
        Set<String> seen = new HashSet<String>();
        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayer))
                continue;
            EntityPlayer player = (EntityPlayer) obj;
            String key = getPlayerKey(player);
            if (key == null)
                continue;
            seen.add(key);
            PlayerCheckData playerData = data.get(key);
            if (playerData == null) {
                playerData = new PlayerCheckData(player);
                data.put(key, playerData);
            }
            playerData.update(player);
        }
        Iterator<String> iterator = data.keySet().iterator();
        while (iterator.hasNext()) {
            if (!seen.contains(iterator.next()))
                iterator.remove();
        }
    }

    public PlayerCheckData get(EntityPlayer player) {
        String key = getPlayerKey(player);
        return key == null ? null : data.get(key);
    }

    /** Create tracking state if missing (used by inbound packet hooks). */
    public PlayerCheckData ensure(EntityPlayer player) {
        String key = getPlayerKey(player);
        if (key == null)
            return null;
        PlayerCheckData playerData = data.get(key);
        if (playerData == null) {
            playerData = new PlayerCheckData(player);
            data.put(key, playerData);
        }
        return playerData;
    }

    public boolean isMovementExempt(EntityPlayer player, PlayerCheckData data) {
        return player == null
                || data == null
                || player.isDead
                || player.ticksExisted < 20
                || data.recentlyTeleported()
                || player.isInWater()
                || player.isInLava()
                || player.isOnLadder()
                || player.isRiding()
                || player.capabilities.isFlying
                || player.capabilities.disableDamage;
    }

    public static String getPlayerKey(EntityPlayer player) {
        if (player == null)
            return null;
        if (player.getUniqueID() != null)
            return player.getUniqueID().toString();
        String name = player.getName();
        return name == null ? String.valueOf(player.getEntityId()) : name;
    }

    public void reset() {
        data.clear();
    }
}
