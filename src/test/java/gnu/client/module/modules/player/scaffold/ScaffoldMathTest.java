package gnu.client.module.modules.player.scaffold;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScaffoldMathTest {

    @Test
    public void avoidAimModulo360KeepsEquivalentYawOutsideSmallRawDeltaWindow() {
        assertEquals(-1.0f, ScaffoldMath.avoidAimModulo360(359.0f, 0.0f), 0.0001f);
        assertEquals(1.0f, ScaffoldMath.avoidAimModulo360(-359.0f, 0.0f), 0.0001f);
        assertEquals(270.0f, ScaffoldMath.avoidAimModulo360(270.0f, 0.0f), 0.0001f);
    }
}
