package gnu.client.module.modules.combat.velocity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class VelocityMoveTest {

    @Test
    public void nullPlayerReturnsSafeDefaults() {
        assertEquals(0.0, VelocityMove.getSpeed(), 0.0);
        assertEquals(0f, VelocityMove.getMoveYaw(), 0f);
        assertFalse(VelocityMove.isMoving());
        VelocityMove.setSpeed(1.0, 90f);
    }
}
