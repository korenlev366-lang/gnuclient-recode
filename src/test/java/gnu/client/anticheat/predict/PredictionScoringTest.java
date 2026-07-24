package gnu.client.anticheat.predict;

import gnu.client.anticheat.CheckRules;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Validates prediction tolerances used by PredictionCheck without needing a World.
 */
public class PredictionScoringTest {

    @Test
    public void tolerances_areBalancedWithLatencySlack() {
        assertTrue(CheckRules.PRED_MAX_POSITION_ERROR >= 0.3);
        assertTrue(CheckRules.PRED_MAX_POSITION_ERROR <= 0.5);
        assertTrue(CheckRules.PRED_LATENCY_SLACK > 0.0);
        assertTrue(CheckRules.PRED_HARD_SPEED_CAP > CheckRules.PRED_MAX_SPEED_ERROR);
        assertTrue(CheckRules.PRED_BUFFER_THRESHOLD >= 3.5);
    }

    @Test
    public void smallError_withinToleranceBand() {
        double posTol = CheckRules.PRED_MAX_POSITION_ERROR + CheckRules.PRED_LATENCY_SLACK;
        assertFalse(0.2 > posTol);
        assertTrue(1.5 > posTol);
    }

    @Test
    public void sync_copiesObservationVelocity() {
        PredictedPlayerState state = new PredictedPlayerState();
        // Minimal stand-in via direct fields (PlayerCheckData needs EntityPlayer).
        state.x = 1;
        state.y = 2;
        state.z = 3;
        state.vx = 0.2;
        state.vy = 0.0;
        state.vz = -0.1;
        state.onGround = true;
        state.initialized = true;
        PredictedPlayerState copy = state.copy();
        assertTrue(copy.x == 1 && copy.vx == 0.2 && copy.onGround);
    }
}
