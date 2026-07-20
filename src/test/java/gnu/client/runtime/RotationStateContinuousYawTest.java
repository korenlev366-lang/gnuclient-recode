package gnu.client.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RotationStateContinuousYawTest {

    @Test
    public void shortArcAcrossWrap() {
        // 170 → -170 should become 190 (170+20), not -170, so lerp does not spin.
        assertEquals(190f, RotationState.continuousYaw(170f, -170f), 0.01f);
    }

    @Test
    public void identityWhenEqual() {
        assertEquals(45f, RotationState.continuousYaw(45f, 45f), 0.01f);
    }

    @Test
    public void smallStepUnchangedBranch() {
        assertEquals(10f, RotationState.continuousYaw(0f, 10f), 0.01f);
    }
}
