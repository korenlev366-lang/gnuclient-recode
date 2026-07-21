package gnu.client.module.modules.player.scaffold;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScaffoldBlocksTest {
    @Test
    public void pickBestSlot_prefersLargerStack() {
        int[] counts = {0, 16, 64, 32};
        boolean[] valid = {false, true, true, true};
        assertEquals(2, ScaffoldBlocks.pickBestSlot(counts, valid));
    }

    @Test
    public void pickBestSlot_tieBreaksLowestIndex() {
        int[] counts = {32, 32, 16};
        boolean[] valid = {true, true, true};
        assertEquals(0, ScaffoldBlocks.pickBestSlot(counts, valid));
    }

    @Test
    public void pickBestSlot_noneValidReturnsNeg1() {
        assertEquals(-1, ScaffoldBlocks.pickBestSlot(new int[]{1}, new boolean[]{false}));
    }
}
