package gnu.client.module.modules.player.scaffold;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScaffoldAimTest {
    @Test
    public void backwards_isOppositeMovementYaw() {
        float moveYaw = 0f;
        float yaw = ScaffoldAim.backwardsYaw(moveYaw);
        assertEquals(180f, ScaffoldRotations.wrap(yaw - moveYaw), 0.01f);
    }

    @Test
    public void godbridge_isDiagonal45() {
        float yaw = ScaffoldAim.godBridgeYaw(0f, true);
        assertEquals(45f, ScaffoldRotations.wrap(yaw), 0.01f);
    }
}
