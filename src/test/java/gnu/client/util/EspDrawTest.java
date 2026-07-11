package gnu.client.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EspDrawTest {

    @Test
    public void defaultFillAlphaIsSoft() {
        assertTrue(EspDraw.DEFAULT_FILL_ALPHA > 0.05f);
        assertTrue(EspDraw.DEFAULT_FILL_ALPHA < 0.35f);
        assertEquals(0.16f, EspDraw.DEFAULT_FILL_ALPHA, 0.001f);
    }

    @Test
    public void resolveAlphaUsesDefaultWhenNonPositive() {
        assertEquals(EspDraw.DEFAULT_FILL_ALPHA, EspDraw.resolveAlpha(0f), 0.001f);
        assertEquals(EspDraw.DEFAULT_FILL_ALPHA, EspDraw.resolveAlpha(-1f), 0.001f);
        assertEquals(0.25f, EspDraw.resolveAlpha(0.25f), 0.001f);
    }
}
