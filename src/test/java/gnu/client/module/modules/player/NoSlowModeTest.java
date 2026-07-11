package gnu.client.module.modules.player;

import net.minecraft.item.EnumAction;
import org.junit.Test;
import static org.junit.Assert.*;

public class NoSlowModeTest {
    @Test
    public void grimAlternates20Then100() {
        // OpenMyau: count++ then even→100 odd→20 (first call odd → 20)
        NoSlowModule.MotionCounter c = new NoSlowModule.MotionCounter();
        assertEquals(20, NoSlowModule.grimMotionPercent(c));
        assertEquals(100, NoSlowModule.grimMotionPercent(c));
        assertEquals(20, NoSlowModule.grimMotionPercent(c));
    }

    @Test
    public void modeIndicesMatchOpenMyau() {
        assertEquals(0, NoSlowModule.MODE_NONE);
        assertEquals(1, NoSlowModule.MODE_VANILLA);
        assertEquals(2, NoSlowModule.MODE_GRIM);
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
