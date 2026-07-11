package gnu.client.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class FloatManagerLogicTest {
    @Test
    public void fallingWhenOnGroundDescending() {
        assertTrue(FloatManager.isFallingEdge(true, 1.0, 1.1, -0.1));
    }

    @Test
    public void notFallingWhenAirborne() {
        assertFalse(FloatManager.isFallingEdge(false, 1.0, 1.1, -0.1));
    }

    @Test
    public void notFallingWhenRising() {
        assertFalse(FloatManager.isFallingEdge(true, 1.1, 1.0, 0.1));
    }
}
