package gnu.client.module.modules.player.scaffold;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScaffoldRotationsTest {
    @Test
    public void clampSpeedRange_swapsWhenMinGreaterThanMax() {
        int[] r = ScaffoldRotations.clampRange(80, 60);
        assertEquals(60, r[0]);
        assertEquals(80, r[1]);
    }

    @Test
    public void stepToward_withFullSpeedReachesTargetWhenGcdDisabled() {
        float[] out = ScaffoldRotations.stepTowardNoGcd(0f, 0f, 90f, 45f, 100);
        assertEquals(90f, out[0], 0.01f);
        assertEquals(45f, out[1], 0.01f);
    }
}
