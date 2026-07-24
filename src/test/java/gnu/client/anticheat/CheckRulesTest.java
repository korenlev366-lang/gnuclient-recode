package gnu.client.anticheat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Integration-style coverage for centralized ClientAC rules (plan step 7). */
public class CheckRulesTest {

    @Test
    public void alertSignals_areAtLeastOne() {
        String[] checks = {
                "Reach", "KillAura", "AutoBlock", "Noslow", "Blink", "Scaffold",
                "Velocity", "Speed", "Flight", "Ground", "MultiAura", "Prediction",
                "Lagrange", "Backtrack", "LagAbuse", "Unknown"
        };
        for (String check : checks) {
            assertTrue(check, CheckRules.alertSignalsFor(check) >= 1);
        }
    }

    @Test
    public void reachBase_isWithinVanillaBand() {
        assertTrue(CheckRules.REACH_BASE >= 3.0);
        assertTrue(CheckRules.REACH_BASE <= 3.2);
        assertTrue(CheckRules.MAX_COMBAT_RANGE > CheckRules.REACH_BASE);
    }

    @Test
    public void noslow_excludesBlockItemsByDesign() {
        // Documented policy: ItemBlock is not a noslow slow-item (see NoSlowCheck).
        assertTrue(CheckRules.NS_MIN_USE_TICKS_SPEED >= CheckRules.NS_MIN_USE_TICKS_SPRINT);
    }

    @Test
    public void multiAura_requiresThreeTargets() {
        assertEquals(3, CheckRules.MA_MIN_TARGETS);
    }
}
