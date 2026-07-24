package gnu.client.module.modules.combat.velocity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AttackReduceVelocityTest {

    @Test
    public void calculateAttackReduceTicksClampsAndScales() {
        // Formula has ~2.94 base offset even at zero horizontal KB.
        assertEquals(3, AttackReduceVelocity.calculateAttackReduceTicks(0, 0));
        int mid = AttackReduceVelocity.calculateAttackReduceTicks(8000, 0);
        assertTrue(mid >= 3 && mid <= 70);
        assertEquals(70, AttackReduceVelocity.calculateAttackReduceTicks(1_000_000, 1_000_000));
    }
}
