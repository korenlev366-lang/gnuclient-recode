package gnu.client.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class EntityCullTest {

    @Test
    public void liteNeverSkipsByCategory() {
        assertFalse(EntityCull.skipByCategory(false, false, true, false, true));
        assertFalse(EntityCull.skipByCategory(false, false, false, true, false));
        assertFalse(EntityCull.skipByCategory(false, false, false, false, true));
    }

    @Test
    public void aggressiveSkipsItemsStandsNonPlayerLiving() {
        assertTrue(EntityCull.skipByCategory(true, false, true, false, false));  // item
        assertTrue(EntityCull.skipByCategory(true, false, false, true, false)); // armor stand
        assertTrue(EntityCull.skipByCategory(true, false, false, false, true)); // mob
        assertFalse(EntityCull.skipByCategory(true, true, false, false, true)); // player
    }

    @Test
    public void aggressiveDistanceUsesHardMax() {
        float max = EntityCull.AGGRESSIVE_MAX_DISTANCE;
        assertEquals(48f, max, 0.001f);
        assertFalse(EntityCull.skipByDistance(true, 40 * 40, max));
        assertTrue(EntityCull.skipByDistance(true, 50 * 50, max));
        assertFalse(EntityCull.skipByDistance(false, 999999, max)); // Lite: distance helper no-op
    }
}
