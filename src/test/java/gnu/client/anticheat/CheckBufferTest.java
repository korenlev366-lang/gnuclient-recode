package gnu.client.anticheat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CheckBufferTest {

    @Test
    public void flag_reachesThreshold() {
        CheckBuffer buffer = new CheckBuffer();
        assertFalse(buffer.flag(1.0, 3.0));
        assertFalse(buffer.flag(1.0, 3.0));
        assertTrue(buffer.flag(1.0, 3.0));
    }

    @Test
    public void decay_doesNotGoNegative() {
        CheckBuffer buffer = new CheckBuffer();
        buffer.flag(0.5, 999.0);
        buffer.decay(10.0);
        assertEquals(0.0, buffer.get(), 0.0001);
    }

    @Test
    public void reset_clearsValue() {
        CheckBuffer buffer = new CheckBuffer();
        buffer.flag(5.0, 999.0);
        buffer.reset();
        assertEquals(0.0, buffer.get(), 0.0001);
    }
}
