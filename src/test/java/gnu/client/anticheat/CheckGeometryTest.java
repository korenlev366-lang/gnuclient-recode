package gnu.client.anticheat;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CheckGeometryTest {

    @Test
    public void distanceToBox_insideIsZero() {
        AxisAlignedBB box = new AxisAlignedBB(0, 0, 0, 2, 2, 2);
        double d = CheckGeometry.distanceToBox(new Vec3(1, 1, 1), box);
        assertEquals(0.0, d, 0.0001);
    }

    @Test
    public void distanceToBox_outsideIsCorrect() {
        AxisAlignedBB box = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
        double d = CheckGeometry.distanceToBox(new Vec3(4, 0.5, 0.5), box);
        assertEquals(3.0, d, 0.0001);
    }

    @Test
    public void clamp_boundsValue() {
        assertEquals(0.0, CheckGeometry.clamp(-1, 0, 5), 0.0);
        assertEquals(5.0, CheckGeometry.clamp(9, 0, 5), 0.0);
        assertEquals(2.0, CheckGeometry.clamp(2, 0, 5), 0.0);
    }

    @Test
    public void rules_geometryConstantsConsistent() {
        assertTrue(CheckRules.HITBOX_EXPAND_XZ > 0);
        assertTrue(CheckRules.HITBOX_EXPAND_Y > 0);
    }
}
