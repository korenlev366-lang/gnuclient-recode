package gnu.client.anticheat.predict;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputPossibilitiesTest {

    @Test
    public void enumerate_includesIdleAndJumpWhenGrounded() {
        List<InputPossibilities.Sample> samples = InputPossibilities.enumerate(true, false);
        assertTrue(samples.size() >= 9);
        boolean hasIdle = false;
        boolean hasJump = false;
        for (InputPossibilities.Sample s : samples) {
            if (s.forward == 0 && s.strafe == 0 && !s.jump)
                hasIdle = true;
            if (s.jump)
                hasJump = true;
        }
        assertTrue(hasIdle);
        assertTrue(hasJump);
    }

    @Test
    public void enumerate_noJumpWhenAirborne() {
        List<InputPossibilities.Sample> samples = InputPossibilities.enumerate(false, false);
        for (InputPossibilities.Sample s : samples) {
            assertFalse(s.jump);
        }
    }

    @Test
    public void movementFromInput_zeroWhenNoKeys() {
        double[] add = InputPossibilities.movementFromInput(null, 0, 0, true, 0);
        assertTrue(Math.abs(add[0]) < 1.0e-6 && Math.abs(add[1]) < 1.0e-6);
    }

    @Test
    public void movementFromInput_forwardProducesMotion() {
        double[] add = InputPossibilities.movementFromInput(null, 1.0F, 0.0F, true, 0.0F);
        assertTrue(Math.hypot(add[0], add[1]) > 0.01);
    }
}
