package gnu.client.anticheat.predict;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PhysicsSimulationTest {

    @Test
    public void endOfTick_appliesGravityWhenAirborne() {
        PredictedPlayerState state = new PredictedPlayerState();
        state.initialized = true;
        state.onGround = false;
        state.x = 0;
        state.y = 10;
        state.z = 0;
        state.vx = 0;
        state.vy = 0;
        state.vz = 0;

        PhysicsSimulation.simulateTickAir(state);

        assertTrue("gravity should pull down after tick", state.vy < 0.0);
        assertEquals(MovementModel.Phase.FALLING, state.phase);
    }

    @Test
    public void ground_friction_reducesHorizontalOverTick() {
        PredictedPlayerState state = new PredictedPlayerState();
        state.initialized = true;
        state.onGround = true;
        state.vx = 0.25;
        state.vz = 0.0;
        state.vy = 0.0;
        double before = Math.abs(state.vx);

        PhysicsSimulation.simulateTickAir(state);

        assertTrue("friction should shrink stored vx", Math.abs(state.vx) < before);
    }

    @Test
    public void fallSpeed_isCapped() {
        PredictedPlayerState state = new PredictedPlayerState();
        state.initialized = true;
        state.onGround = false;
        state.vy = -10.0;

        PhysicsSimulation.simulateTickAir(state);

        assertTrue(state.vy >= -MovementModel.MAX_FALL_SPEED - 0.01);
    }

    @Test
    public void jumpSample_setsUpwardVelocity() {
        PredictedPlayerState state = new PredictedPlayerState();
        state.initialized = true;
        state.onGround = true;
        state.x = 0;
        state.y = 64;
        state.z = 0;

        InputPossibilities.Sample jump = new InputPossibilities.Sample(0, 0, true, false);
        PhysicsSimulation.StepOutcome out = PhysicsSimulation.simulateSample(state, null, null, jump);
        assertTrue(out.vy > 0.0 || out.y > state.y);
    }
}
