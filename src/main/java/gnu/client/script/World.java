package gnu.client.script;

import gnu.client.runtime.mc.Mc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Collections;
import java.util.List;

/**
 * Script-facing {@code world} accessor — stateless singleton facade over {@link Mc}.
 */
public final class World {

    public static final World INSTANCE = new World();

    private World() {}

    /** Block state at the given integer coords, or {@code null} if no world. */
    public Object getBlockAt(int x, int y, int z) {
        return Mc.getBlockState(Mc.world(), x, y, z);
    }

    /** Loaded entities in the client world. Returns an empty list if no world. */
    public List<?> getEntities() {
        if (Mc.world() == null)
            return Collections.emptyList();
        return Mc.getWorldEntities(Mc.world());
    }

    public boolean isPlayer(Object entity) {
        return Mc.isEntityPlayer(asEntity(entity));
    }

    public double distanceTo(Object entity) {
        return Mc.distanceToPlayer(asEntity(entity));
    }

    /** Nearest other player within {@code range} blocks, or {@code null}. */
    public Object getNearestPlayer(double range) {
        EntityPlayer nearest = Mc.getNearestPlayer(range);
        return nearest;
    }

    private static Entity asEntity(Object entity) {
        return entity instanceof Entity ? (Entity) entity : null;
    }
}
