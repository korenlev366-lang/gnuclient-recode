package gnu.client.module.modules.player;

import net.minecraft.item.EnumAction;
import org.junit.Test;
import static org.junit.Assert.*;

public class NoSlowModeTest {
    @Test
    public void grimOffSlotStaysOffUseSlot() {
        // While using we stay on off-slot (not flip back) for MultiActions exempt.
        assertEquals(1, NoSlowModule.nextGrimTarget(0, 1, true, -1));
        assertEquals(1, NoSlowModule.nextGrimTarget(0, 1, true, 0));
        assertEquals(2, NoSlowModule.nextGrimTarget(1, 1, true, 1));
    }

    @Test
    public void modeIndicesMatchWsamiaw() {
        assertEquals(0, NoSlowModule.MODE_NONE);
        assertEquals(1, NoSlowModule.MODE_VANILLA);
        assertEquals(2, NoSlowModule.MODE_GRIM);
        assertEquals(2, NoSlowModule.MODE_FLOAT);
        assertEquals(3, NoSlowModule.MODE_FOOD_GRIM);
    }

    @Test
    public void eatingMatchesOpenMyauEnumAction() {
        assertTrue(NoSlowModule.matchesEatingUseAction(EnumAction.EAT, false));
        assertTrue(NoSlowModule.matchesEatingUseAction(EnumAction.DRINK, false));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.EAT, true));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.DRINK, true));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.NONE, false));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.BLOCK, false));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.BOW, false));
        assertFalse(NoSlowModule.isEatingStack(null));
    }
}
